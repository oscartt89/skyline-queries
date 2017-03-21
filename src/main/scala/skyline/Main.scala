package skyline

import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

object Main {
  def main(args: Array[String]): Unit = {
    if(args.length < 3){
        println("ERROR. Use: run <STREAM SIZE> <N DIMENSIONS> <MAX RANDOM NUMBER> <N WORKERS>")
        System.exit(-1)
    }

    val systemName = "SkylineApp"
    val r1 = new Random(1)
    val stream = make(args(0).toInt, args(1).toInt, args(2).toInt, r1)
    val nWorkers = args(3).toInt

    //val stream = Point(Array(3, 3)) #:: Point(Array(4, 2)) #:: Point(Array(1, 3)) #:: Point(Array(2, 2)) #:: Point(Array(4, 1)) #:: Stream.empty

    val system1 = ActorSystem(systemName)
    val joinAddress = Cluster(system1).selfAddress
    Cluster(system1).join(joinAddress)
    system1.actorOf(Props[MemberListener], "memberListener")
    for(i <- 1 to nWorkers){
        system1.actorOf(Worker.props("in", "out", "worker" + i))
    }
    system1.actorOf(Streamer.props("in", stream))
    system1.actorOf(Writer.props("out", nWorkers))

    //Thread.sleep(5000)
    /*val system2 = ActorSystem(systemName)
    Cluster(system2).join(joinAddress)
    system2.actorOf(Worker.props("in", "out", "worker" + (nWorkers+1)))*/

    /*val system3 = ActorSystem(systemName)
    Cluster(system3).join(joinAddress)
    system3.actorOf(Props[Worker], "worker4")
    system3.actorOf(Props[Worker], "worker5")*/

    Await.result(system1.whenTerminated, Duration.Inf)
  }

  def make(i:Int, dimensions: Int, max: Int, r: Random) : Stream[Point] = {                  
    if(i==0){
        Stream.empty[Point] 
    } else {
        val d = Array.fill(dimensions)(r.nextInt(max))
        val point = Point(d)
//        println("Point(" + i + "): [" + point + "]")
        Stream.cons(point, make(i-1, dimensions, max, r))    
    }           
  }
}
