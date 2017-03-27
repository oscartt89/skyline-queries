package skyline

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Put, SendToAll}
import scala.collection.mutable.SortedSet

object Worker {
  def props(in: String, name: String) = Props(new Worker(in, name))
  case class Next()
  case class Filter(name: String, p: Point)
}

class Worker(in: String, name: String) extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  var localSkyline: SortedSet[Point] = SortedSet.empty[Point]

  //mediator ! Put(self)

  self ! Worker.Next()

  def receive = {
    case Worker.Next() => mediator ! Publish(in, Streamer.SendNext(self))
    case i: Point => {
      println(name + "got point: " + i)
      val point = work(i)
      point match {
        case Some(value) => {
          context.actorSelection("../**") ! Worker.Filter(name, value)
        }
        case None => {}
      }
      self ! Worker.Next()
    }
    case Worker.Filter(w, value) => {
      if(w != name){
        //println(name + "received point " + value + "to filter the localSkyline " + localSkyline.mkString(", "))
        //val t0 = System.currentTimeMillis
        if(localSkyline.exists(value.dominates(_)))
          localSkyline = localSkyline.filter(!value.dominates(_))
        println(localSkyline.mkString(", "))
        //val t1 = System.currentTimeMillis
        //println("[" + name + "] Filtering the point: " + (t1 - t0) + "(ms)")
      } else {
        println(localSkyline.mkString(", "))
      }
    }
  }

  def work(i: Point): Option[Point] = {
    if (localSkyline.size == 0) {
      //println(name + " adding point: [" + i + "]")
      localSkyline += i
      Some(i)
    } else {
      //val t0 = System.currentTimeMillis
      var dominated = localSkyline.exists(_.dominates(i))
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
}