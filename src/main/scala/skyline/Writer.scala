package skyline

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import scala.collection.mutable.SortedSet
import scala.collection.mutable.Map

object Writer {
  def props(topic: String, nWorkers: Int) = Props(new Writer(topic, nWorkers))
}

class Writer(topic: String, nWorkers: Int) extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  var n = nWorkers
  var globalSkyline: Map[String, SortedSet[Point]] = Map[String, SortedSet[Point]]()

  mediator ! Subscribe(topic, self)

  def receive = {
    case Worker.IdentifiedPoint(w, p) => {
  		if(include(w, p)) {
  			//println("writer added (" + p + "). current globalSkyline: " + globalSkyline)
  		}
    }
    case Streamer.Done() => {
    	n = n - 1
    	if(n == 0)
    		context.system.terminate()
    }
  }

  def include (w: String, i: Point): Boolean = {
    var dominated = false
  	if (!globalSkyline.contains(w)) {
//  		println("writer adding point: [" + i + "]")
  		globalSkyline += (w -> (SortedSet[Point](i)))
  	} else {
      for((ww, local) <- globalSkyline) {
        var tmp = local
        if(w == ww){
          //Here just add the point without checking, and then filters the points dominated
          //by the included one
          tmp = tmp.filter(!i.dominates(_))
          tmp += i
        } else {
    		  dominated = tmp.exists(_.dominates(i))
      		if(!dominated) {
//    			println("writer adding point2: [" + i + "]. globalSkyline: " + globalSkyline.mkString(", "))
      			tmp = tmp.filter(!i.dominates(_))
//     			println("writer added point: [" + i + "]. globalSkyline (after filtering): " + globalSkyline.mkString(", "))
      		} else {
//    			print("writer point: [" + i + "] dominated by the globalSkyline: " + globalSkyline.mkString(", "))
      		}
        }
        globalSkyline += (w -> tmp)
      }
  	}
    !dominated
  }
}