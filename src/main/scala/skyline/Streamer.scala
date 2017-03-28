package skyline

import akka.actor.Actor
import akka.actor.PoisonPill
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import scala.collection.mutable.SortedSet

object Streamer {
  def props(topic: String, stream: Stream[Point], nWorkers: Int): Props = Props(classOf[Streamer], topic, stream, nWorkers)

  case class SendNext(to: ActorRef)
  case class Filter(origin: String, p: Point, localSkyline: SortedSet[Point])
  case class Done()
  case class TerminationAck()
}

class Streamer(topic: String, stream: Stream[Point], nWorkers: Int) extends Actor {
  val mediator = DistributedPubSub(context.system).mediator
  val iterator = stream.iterator
  var n = nWorkers

  mediator ! Subscribe(topic, self)

  def receive = {
    case Streamer.SendNext(to) => {
      if(iterator.hasNext) {
        val point = iterator.next()
        //println("Streamer sending point: " + point)
        to ! point
      } else {
        to ! Streamer.Done()
        //println("stream empty. sending shutdown message to the workers")
      }
    }
    case Streamer.TerminationAck() => {
      n = n - 1
      if(n == 0)
        context.system.terminate()
    }
  }

}