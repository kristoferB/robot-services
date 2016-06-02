package robotServices.launcher

import addInstruction.InstructionFiller
import akka.actor.ActorSystem
import akka.event.Logging
import cycleStore.CycleAggregator
import isWaitInstruction.IsWaitFiller
import routineChange.RoutineExtractor
import cycleChange.CycleChange
import tipDressWear.TipDressTransformer
import waitChange.WaitChange

/**
  * Created by Henrik on 2016-05-02.
  */

object launcher extends App {
  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  val logger = Logging(system, "SimpleService")

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

  /*// Remove comment to test the system using the provided tester actor
  val testerActor = system.actorOf(robotServices.launcher.testMessageSender.props)
  testerActor ! "connect"*/

  scala.io.StdIn.readLine("Press ENTER to exit application.\n") match {
    case x => system.terminate()
  }
}