package skyline

import akka.actor.ActorSystem
import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.ActorMaterializer
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.util.ByteString
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

object Main {
  def main(args: Array[String]): Unit = {
    if(args.length < 1){
        println("ERROR. Use: run <N WORKERS>")
        System.exit(-1)
    }

    val systemName = "SkylineApp"
    val nWorkers = args(0).toInt

    val system1 = ActorSystem(systemName)
    val joinAddress = Cluster(system1).selfAddress
    val mediator = DistributedPubSub(system1).mediator
    Cluster(system1).join(joinAddress)
    system1.actorOf(Props[MemberListener], "memberListener")
    system1.actorOf(Streamer.props("in", nWorkers))
    for(i <- 1 to nWorkers){
        system1.actorOf(Worker.props("in", "query", "worker" + i))
    }

    //Thread.sleep(5000)
    /*val system2 = ActorSystem(systemName)
    Cluster(system2).join(joinAddress)
    system2.actorOf(Worker.props("in", "out", "worker" + (nWorkers+1)))*/

    /*val system3 = ActorSystem(systemName)
    Cluster(system3).join(joinAddress)
    system3.actorOf(Props[Worker], "worker4")
    system3.actorOf(Props[Worker], "worker5")*/

    implicit val actorSystem = system1
    implicit val streamMaterializer = ActorMaterializer()
    implicit val executionContext = actorSystem.dispatcher
    val log = actorSystem.log

    import PointJsonProtocol._

    def addRoute =
        pathPrefix("skyline") {
          path("add-point") {
            (post & entity(as[Point2])) { point =>
              mediator ! Publish("in", Streamer.AddPoint(Point(point.data)))
              complete("Point included\n")
            }
        }
    }

    def queryRoute =
        pathPrefix("skyline") {
            path("query") {
              get {
                // in addition to sending events through the scheduler inside the actor
                val source = Source.actorPublisher[QueryActor.Skyline](QueryActor.props("query"))
                  .map(sky => ByteString(sky.data.toList.mkString("; ") + "\n"))
                  //.map(s => ByteString(s + ","))
                complete(HttpEntity(`text/plain(UTF-8)`, source))
              }
            }
        }

    val allRoutes = addRoute ~ queryRoute

    val bindingFuture = Http().bindAndHandle(allRoutes, "localhost", 9000)
    bindingFuture
      .map(_.localAddress)
      .map(addr => s"Bound to $addr")
      .foreach(log.info)
  }
}