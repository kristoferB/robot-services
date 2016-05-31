package robotServices.launcher

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer

/**
  * Created by Henrik on 2016-05-02.
  */

object launcher extends App {
  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
  val logger = Logging(system, "SimpleService")

  val fillWithInstructionActor = system.actorOf(robotpath.service.PointerTransformer.props)
  fillWithInstructionActor ! "connect"

  val waitTestActor = system.actorOf(robotiswaiting.service.PointerTransformer.props)
  waitTestActor ! "connect"

  import robotroutinechange.service._
  val routineChangeActor = system.actorOf(RoutineExtractor.props)
  routineChangeActor ! "connect"

  /*import robotcyclestore.service._
  val cycleAggregatorActor = system.actorOf(CycleAggregator.props)
  cycleAggregatorActor ! "connect"*/

  /*import robottipdresswear.service._
  val cutterWarnActor = system.actorOf(TipDressTransformer.props)
  cutterWarnActor ! "connect"*/

  /*// Remove comment to test the system using the provided tester actor
  val testerActor = system.actorOf(robotServices.launcher.testMessageSender.props)
  testerActor ! "connect"*/

  scala.io.StdIn.readLine("Press ENTER to exit application.\n") match {
    case x => system.terminate()
  }
}