package skyline

import akka.actor.{ActorLogging, Props}
import akka.actor.ActorRef
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish}
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import skyline.QueryActor._

import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.language.postfixOps
import scala.util.Random
import scala.collection.mutable.SortedSet

object QueryActor {
  sealed trait Command
  case class SkylineQuery(queryActor: ActorRef) extends Command

  sealed trait Event
  case class Skyline(data: SortedSet[Point]) extends Event

  def props(query: String) = Props(new QueryActor(query))
}

/**
  * An actor that publishes Skyline messages and respects backpressure
  * This actor is meant to be created when using a Source.fromActorPublisher to allow integration between Actors
  * and Streams.
  *
  * The idea is that if you want to push some messages from your existing Actors into a Stream, you would
  * create a Publisher and feed your existing ActorRef into the Publisher and have the Publisher constantly ask your
  * Actor for messages which it emits into the Stream (this is not done here). When the Stream is cancelled, only
  * this Actor dies leaving your Actors intact without causing any disruptions. This is really an intermediate actor
  * that you use when you want to push data from your Actors into Streams.
  *
  * Credits: http://doc.akka.io/docs/akka/2.4.10/scala/stream/stream-integrations.html#ActorPublisher
  */
class QueryActor (query: String) extends ActorPublisher[Skyline] with ActorLogging {
  val MaxBufferSize = 100
  var buffer = Vector.empty[Skyline]
  val mediator = DistributedPubSub(context.system).mediator
  implicit val ec = context.dispatcher
  
  //Announce that we have a Skyline Query
  mediator ! Publish(query, SkylineQuery(self))

  @tailrec
  private def deliverBuffer(): Unit =
    if (totalDemand > 0 && isActive) {
      // You are allowed to send as many elements as have been requested by the stream subscriber
      // total demand is a Long and can be larger than what the buffer has
      if (totalDemand <= Int.MaxValue) {
        val (sendDownstream, holdOn) = buffer.splitAt(totalDemand.toInt)
        buffer = holdOn
        // send the stuff downstream
        sendDownstream.foreach(onNext)
      } else {
        val (sendDownStream, holdOn) = buffer.splitAt(Int.MaxValue)
        buffer = holdOn
        sendDownStream.foreach(onNext)
        // recursive call checks whether is more demand before repeating the process
        deliverBuffer()
      }
    }


  override def receive: Receive = {
    case sky: Skyline if buffer.size == MaxBufferSize =>
      log.warning("received a SplitString message when the buffer is maxed out")

    case sky: Skyline =>
      if (buffer.isEmpty && totalDemand > 0 && isActive) {
        // send elements to the stream immediately since there is demand from downstream and we
        // have not buffered anything so why bother buffering, send immediately
        // You send elements to the stream by calling onNext
        onNext(sky)
      }
      else {
        // there is no demand from downstream so we will store the sky in our buffer
        // Note that :+= means add to end of Vector
        buffer :+= sky
      }

    // A request from down stream to send more data
    // When the stream subscriber requests more elements the ActorPublisherMessage.Request message is
    // delivered to this actor, and you can act on that event. The totalDemand is updated automatically.
    case Request(_) => deliverBuffer()

    // When the stream subscriber cancels the subscription the ActorPublisherMessage.Cancel message is
    // delivered to this actor. If the actor is stopped the stream will be completed, unless it was not
    // already terminated with failure, completed or canceled.
    case Cancel =>
      log.info("Stream was cancelled")
      context.stop(self)

    case other =>
      log.warning(s"Unknown message $other received")
  }
}
