package skyline

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import scala.collection.mutable.Queue

object Streamer {
  def props(topic: String, nWorkers: Int): Props = Props(classOf[Streamer], topic, nWorkers)

  case class SendNext(to: ActorRef)
  case class AddPoint(p: Point)
}

class Streamer(topic: String, nWorkers: Int) extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  var queue = Queue.empty[Point]
  var waitingActors = Queue.empty[ActorRef]
  var n = nWorkers
  var nextActor = self

  mediator ! Subscribe(topic, self)

  def receive = {
    case Streamer.SendNext(to) => {
      if(!queue.isEmpty) {
        val point = queue.dequeue
        to ! point
      } else {
        //Queue is empty, adding actor 'to' to the waitingActors queue
        waitingActors += to
      }
    }
    case Streamer.AddPoint(p) => {
      //println("Received point: " + p)
      queue += p
      //Check who was waiting
      if(!waitingActors.isEmpty){
        //Sending to a ready worker in the requests order
        nextActor = waitingActors.dequeue
        nextActor ! p
        queue.dequeue
      }
    }
  }
}