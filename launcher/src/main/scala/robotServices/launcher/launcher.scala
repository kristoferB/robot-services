package robotServices.launcher

import addInstruction.InstructionFiller
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import core.Config
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

  implicit var system: ActorSystem = ActorSystem()
  var log: LoggingAdapter = Logging(system, "launcher")

  def main(args: Array[String]): Unit = {
    startServices()
    scala.io.StdIn.readLine("Press any key to exit.\n")
    stop(Array.empty)
  }

  def startServices() = {
    log.info("Starting")
    log.info("Base dir: " + Config.jarDir)
    log.info("External config file: " + Config.extConfFile)
    log.info("extConfFile.exists && !extConfFile.isDirectory: " + (Config.extConfFile.exists && !Config.extConfFile.isDirectory))

    implicit val executor = system.dispatcher

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

    log.info("Started")

    /*// Remove comment to test the system using the provided tester actor
    val testerActor = system.actorOf(robotServices.launcher.testMessageSender.props)
    testerActor ! "connect"*/
  }

  var stopped = false

  def start(args: Array[String]): Unit = {
    startServices()
    while(!stopped)
      Thread.sleep(1000)
  }

  def stop(args: Array[String]): Unit = {
    log.info("Stopping the services. This may take a while.")
    stopped = true
    system.terminate()
  }
}