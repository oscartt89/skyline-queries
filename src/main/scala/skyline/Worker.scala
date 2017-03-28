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
  case class IdentifiedPoint(worker: String, value: Point, otherLocal: SortedSet[Point])
}

class Worker(in: String, name: String) extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  var localSkyline: SortedSet[Point] = SortedSet.empty[Point]
  var remoteSkyline: Map[String, SortedSet[Point]] = Map[String, SortedSet[Point]]()

  //mediator ! Put(self)

  self ! Worker.Next()

  def receive = {
    case Worker.Next() => mediator ! Publish(in, Streamer.SendNext(self))
    case i: Point => {
      val point = work(i)
      point match {
        case Some(value) => {
          context.actorSelection("../**") ! Streamer.Filter(name, value, localSkyline)
          //mediator ! SendToAll(path = "../**", msg = Streamer.Filter(name, value), allButSelf = false)
          //mediator ! Publish(in, Streamer.Filter(name, value))
          //self ! Streamer.Filter(name, value)
        }
        case None => {}
      }
      self ! Worker.Next()
    }
    case Streamer.Filter(w, value, otherLocal) => {
      if(w != name){
        //Update the local skyline
        if(localSkyline.exists(value.dominates(_)))
          localSkyline = localSkyline.filter(!value.dominates(_))

        //Also update the remote skyline
        remoteSkyline += (w -> otherLocal)
      }
    }
    case Streamer.Done() => {
      //Sending finalisation message to the writer
      //println(name + " forwarding the shutdown message to the writer")
      //println(localSkyline.mkString(", "))
      mediator ! Publish(in, Streamer.TerminationAck())
      context.stop(self)
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