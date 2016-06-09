package robotServices.launcher

import addInstruction.InstructionFiller
import akka.actor.ActorSystem
//import akka.event.Logging
import cycleStore.CycleAggregator
import isWaitInstruction.IsWaitFiller
import routineChange.RoutineExtractor
import cycleChange.CycleChange
import tipDressWear.TipDressTransformer
import waitChange.WaitChange

/**
  * Created by Henrik on 2016-05-02.
  */

object launcher {

  implicit var system: ActorSystem = null

  def main(args: Array[String]): Unit = {
    if (args.length > 0) {
      if ("start".equals(args(0)))
        start()
      else if ("stop".equals(args(0)))
        stop()
      else if ("console".equals(args(0))) {
        start()
        scala.io.StdIn.readLine("Press any key to exit.\n")
        stop()
      }
    }
  }

  private def start() = {
    println("Starting")

    system = ActorSystem()
    implicit val executor = system.dispatcher
    //val logger = Logging(system, "SimpleService")

    val fillWithInstructionActor = system.actorOf(InstructionFiller.props)
    fillWithInstructionActor ! "connect"

    val waitTestActor = system.actorOf(IsWaitFiller.props)
    waitTestActor ! "connect"

    val routineChangeActor = system.actorOf(RoutineExtractor.props)
    routineChangeActor ! "connect"

    val cycleEventActor = system.actorOf(CycleChange.props)
    cycleEventActor ! "connect"

    val cycleAggregatorActor = system.actorOf(CycleAggregator.props)
    cycleAggregatorActor ! "connect"

    val cutterWarnActor = system.actorOf(TipDressTransformer.props)
    cutterWarnActor ! "connect"

    val waitChangeActor = system.actorOf(WaitChange.props)
    waitChangeActor ! "connect"

    println("Started")

    /*// Remove comment to test the system using the provided tester actor
    val testerActor = system.actorOf(robotServices.launcher.testMessageSender.props)
    testerActor ! "connect"*/
  }

  private def stop() = {
    println("Stopping")
    system.terminate()
    println("Stopped")
  }
}