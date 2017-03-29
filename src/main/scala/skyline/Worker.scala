package skyline

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import scala.collection.mutable.SortedSet
import scala.concurrent.duration._

object Worker {
  def props(in: String, query: String, name: String) = Props(new Worker(in, query, name))
  case class Next()
  case class Filter(origin: String, p: Point, localSkyline: SortedSet[Point])
}

class Worker(in: String, query: String, name: String) extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  var localSkyline: SortedSet[Point] = SortedSet.empty[Point]
  var remoteSkyline: Map[String, SortedSet[Point]] = Map[String, SortedSet[Point]]()
  var queryActor = self

  mediator ! Subscribe(query, self)

  self ! Worker.Next()

  def receive = {
    case Worker.Next() => mediator ! Publish(in, Streamer.SendNext(self))
    case i: Point => {
      //println(name + "got point: " + i)
      val point = work(i)
      point match {
        case Some(value) => {
          context.actorSelection("../**") ! Worker.Filter(name, value, localSkyline)
          if(queryActor != self) {
            val result = localSkyline
            for((ww, remote) <- remoteSkyline) {
              result ++= remote.filter(!i.dominates(_))
            }
            queryActor ! QueryActor.Skyline(result)
          }
        }
        case None => {}
      }
      self ! Worker.Next()
    }
    case Worker.Filter(w, value, otherLocal) => {
      if(w != name){
        //Update the local skyline
        if(localSkyline.exists(value.dominates(_)))
          localSkyline = localSkyline.filter(!value.dominates(_))

        //Also update the remote skyline
        remoteSkyline += (w -> otherLocal)
      }
    }
    case QueryActor.SkylineQuery(qA) => {
      //println(name + " will be serving the queries")
      queryActor = qA
    }
  }

  def work(i: Point): Option[Point] = {
    var dominated = false
    
    //Check if it is dominated by the localSkyline
    dominated = localSkyline.exists(_.dominates(i))
    //Check if it is dominated by the remote skylines
    if(!remoteSkyline.isEmpty) {
      for((ww, remote) <- remoteSkyline) {
        if(!dominated)
          dominated = remote.exists(_.dominates(i))
      }
    }
    //val t1 = System.currentTimeMillis
    //println("[" + name + "] Checking if dominated: " + (t1 - t0) + "(ms)")
    if(!dominated) {
      //println(name + " adding point2: [" + i + "]")
      //println(name + " before filtering. localSkyline: " + localSkyline.mkString(", "))
      //var t0 = System.currentTimeMillis
      if(localSkyline.exists(i.dominates(_)))
        localSkyline = localSkyline.filter(!i.dominates(_))
      //var t1 = System.currentTimeMillis
      //println("[" + name + "] Filtering the list: " + (t1 - t0) + "(ms)")
      //t0 = System.currentTimeMillis
      localSkyline += i
      //t1 = System.currentTimeMillis
      //println("[" + name + "] Inserting the point: " + (t1 - t0) + "(ms)")
      //println(name + " after filtering. localSkyline: " + localSkyline.mkString(", "))
      Some(i)
    } else {
      //Point dominated
      //println(name + " point: [" + i + "] dominated by the localSkyline:" + localSkyline.mkString(", "))
      None
    }
  }
}