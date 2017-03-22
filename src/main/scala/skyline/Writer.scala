package skyline

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import scala.collection.immutable.SortedSet

object Writer {
  def props(topic: String, nWorkers: Int) = Props(new Writer(topic, nWorkers))
}

class Writer(topic: String, nWorkers: Int) extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  var n = nWorkers
  var globalSkyline: SortedSet[Point] = SortedSet.empty[Point]

  mediator ! Subscribe(topic, self)

  def receive = {
    case value: Point => {
  		if(include(value)) {
  			//println("writer added (" + value + "). current globalSkyline: " + globalSkyline.mkString(", "))
  		}
    }
    case Streamer.Done() => {
    	n = n - 1
    	if(n == 0)
    		context.system.terminate()
    }
  }

  def include (i: Point): Boolean = {
    var dominated = false
  	if (globalSkyline.size == 0) {
  		//println("writer adding point: [" + i + "]")
  		globalSkyline += i
  	} else {
  		dominated = globalSkyline.exists(_.dominates(i))
  		if(!dominated) {
  			//println("writer adding point2: [" + i + "]. globalSkyline: " + globalSkyline.mkString(", "))
  			globalSkyline = globalSkyline.filter(!i.dominates(_))
  			globalSkyline += i
  			//println("writer added point: [" + i + "]. globalSkyline (after filtering): " + globalSkyline.mkString(", "))
  		} else {
  			//print("writer point: [" + i + "] dominated by the globalSkyline: " + globalSkyline.mkString(", "))
  		}
  	}
    !dominated
  }
}