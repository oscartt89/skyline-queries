package skyline

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import scala.collection.mutable.SortedSet

object Worker {
  def props(in: String, out: String, name: String) = Props(new Worker(in, out, name))
  case class Next()
  case class IdentifiedPoint(worker: String, value: Point)
}

class Worker(in: String, out: String, name: String) extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  var localSkyline: SortedSet[Point] = SortedSet.empty[Point]

  self ! Worker.Next()

  def receive = {
    case Worker.Next() => mediator ! Publish(in, Streamer.SendNext(self))
    case i: Point => {
      val point = work(i)
      point match {
        case Some(value) => {
          mediator ! Publish(out, Worker.IdentifiedPoint(name, value))
        }
        case None => {}
      }
      self ! Worker.Next()
    }
    case Streamer.Done() => {
      //Sending finalisation message to the writer
      //println(name + " forwarding the shutdown message to the writer")
      mediator ! Publish(out, Streamer.Done())
    }
  }

  def work(i: Point): Option[Point] = {
    if (localSkyline.size == 0) {
      //println(name + " adding point: [" + i + "]")
      localSkyline += i
      Some(i)
    } else {
      var dominated = localSkyline.exists(_.dominates(i))
      if(!dominated) {
        //println(name + " adding point2: [" + i + "]")
        //println(name + " before filtering. localSkyline: " + localSkyline.mkString(", "))
        if(localSkyline.exists(i.dominates(_)))
          localSkyline = localSkyline.filter(!i.dominates(_))
        localSkyline += i
        //println(name + " after filtering. localSkyline: " + localSkyline.mkString(", "))
        Some(i)
      } else {
        //Point dominated
        //println(name + " point: [" + i + "] dominated by the localSkyline:" + localSkyline.mkString(", "))
        None
      }
    }
  }
}