package skyline

import akka.actor.Actor
import akka.actor.PoisonPill
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}

object Streamer {
  def props(topic: String, stream: Stream[Point]): Props = Props(classOf[Streamer], topic, stream)

  case class SendNext(to: ActorRef)
  case class Done()
}

class Streamer(topic: String, stream: Stream[Point]) extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  val iterator = stream.iterator

  mediator ! Subscribe(topic, self)

  def receive = {
    case Streamer.SendNext(to) =>
      if(iterator.hasNext) {
        val point = iterator.next()
//        println("Streamer sending point: " + point)
        to ! point
      } else {
        to ! Streamer.Done()
        //println("stream empty. sending shutdown message to the workers")
      }
  }

}