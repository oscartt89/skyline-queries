package skyline

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import scala.collection.immutable.SortedSet

object Worker {
  def props(in: String, out: String, name: String) = Props(new Worker(in, out, name))
  case class Next()
  case class PointOption(worker: String, value: Option[Point])
}

class Worker(in: String, out: String, name: String) extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  var localSkyline: SortedSet[Point] = SortedSet.empty[Point]

  self ! Worker.Next()

  def receive = {
    case Worker.Next() => mediator ! Publish(in, Streamer.SendNext(self))
    case i: Point => {
      mediator ! Publish(out, work(i))
      self ! Worker.Next()
    }
    case Streamer.Done() => {
      //println(name + " forwarding the shutdown message to the writer")
      mediator ! Publish(out, Streamer.Done())
    }
  }

  def work(i: Point): Worker.PointOption = {
    if (localSkyline.size == 0) {
//      println(name + " adding point: [" + i + "]")
      localSkyline += i
      Worker.PointOption(name, Some(i))
    } else {
      var dominated = localSkyline.exists(_.dominates(i))
      if(!dominated) {
//        println(name + " adding point2: [" + i + "]")
//        println(name + " before filtering. localSkyline: " + localSkyline.mkString(", "))
        localSkyline = localSkyline.filter(!i.dominates(_))
        localSkyline += i
//        println(name + " after filtering. localSkyline: " + localSkyline.mkString(", "))
        Worker.PointOption(name, Some(i))
      } else {
//        println(name + " point: [" + i + "] dominated by the localSkyline:" + localSkyline.mkString(", "))
        Worker.PointOption(name, None)
      }
    }
  }
}