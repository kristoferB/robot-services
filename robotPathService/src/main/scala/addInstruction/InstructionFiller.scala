package addInstruction

import akka.actor._
import com.codemettle.reactivemq.ReActiveMQMessages._
import com.codemettle.reactivemq._
import com.codemettle.reactivemq.model._
import core.ServiceBase
import core.Domain._
import core.Helpers._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization.write

/**
  * Created by Henrik on 2016-04-08.
  */

class InstructionFiller extends ServiceBase {
  // Type aliases
  type RobotName = String
  type TaskName = String
  type ModuleName = String
  type Instruction = String

  // Maps
  var robotMap: Map[RobotName, Map[TaskName, Map[ModuleName, Module]]] =
    Map[RobotName, Map[TaskName, Map[ModuleName, Module]]]()
  var taskMap: Map[TaskName, Map[ModuleName, Module]] = Map[TaskName, Map[ModuleName, Module]]()
  var moduleMap: Map[ModuleName, Module] = Map[ModuleName, Module]()

  // Functions
  def receive = {
    case "connect" =>
      ReActiveMQExtension(context.system).manager ! GetAuthenticatedConnection(s"nio://$address:61616", user, pass)
    case ConnectionEstablished(request, c) =>
      println("Connected: " + request)
      c ! ConsumeFromTopic(topic)
      theBus = Some(c)
    case ConnectionFailed(request, reason) =>
      println("Connection failed: " + reason)
    case mess @ AMQMessage(body, prop, headers) =>
      val json = parse(body.toString)
      if (json.has("readValue")) {
        val event: ModulesReadEvent = json.extract[ModulesReadEvent]
        event.readValue.foreach(task => {
          task.modules.foreach(module => moduleMap += (module.name -> module))
          taskMap += (task.name -> moduleMap)
          moduleMap = Map.empty[ModuleName, Module]
        })
        robotMap += (event.robotId -> taskMap)
        taskMap = Map.empty[TaskName, Map[ModuleName, Module]]
      } else if (json.has("programPointerPosition") && !json.has("instruction")) {
        val event: PointerChangedEvent = json.extract[PointerChangedEvent]
        fill(event)
      } else {
        // do nothing... OR println("Received message of unmanageable type property.")
      }
  }

  def fill(event: PointerChangedEvent) = {
    val eventPPPos = event.programPointerPosition
    if (robotMap.contains(event.robotId)) {
      if (robotMap(event.robotId).contains(eventPPPos.task)) {
        if (robotMap(event.robotId)(eventPPPos.task).contains(eventPPPos.position.module)) {
          val module: Module = robotMap(event.robotId)(eventPPPos.task)(eventPPPos.position.module)
          val range: Range = eventPPPos.position.range
          val instruction: Instruction = module.file(range.begin.row).
            slice(range.begin.column - 1, range.end.column + 1)
          val filledEvent: PointerWithInstruction =
            PointerWithInstruction(event.robotId, event.workCellId, event.address, instruction, eventPPPos)
          val json = write(filledEvent)
          println("From instruction filler: " + json)
          sendToBus(json)
        } else
          println(s"The system ${event.robotId} does not contain the module called" +
            s"${eventPPPos.position.module}")
      } else
        println(s"The system ${event.robotId} does not contain the task called" +
          s"${eventPPPos.task}")
    } else
      requestModules(event)
  }

  def requestModules(event: PointerChangedEvent) = {
    import org.json4s.JsonDSL._
    val jContents = ("robotId" -> event.robotId) ~ ("service" -> "instructionFiller")
    val json = "newRobotEncountered" -> jContents
    println(s"Requesting modules for robot id ${event.robotId}." + json)
    sendToBus(write(json))
  }
}

object InstructionFiller {
  def props = Props[InstructionFiller]
}