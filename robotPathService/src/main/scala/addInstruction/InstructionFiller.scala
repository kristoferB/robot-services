package addInstruction

import akka.actor._
import core.ServiceBase
import core.Domain._
import core.Helpers._
import org.json4s._
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
  var robotMap: Map[RobotName, Map[TaskName, Map[ModuleName, Module]]] = Map.empty
  var taskMap: Map[TaskName, Map[ModuleName, Module]] = Map.empty
  var moduleMap: Map[ModuleName, Module] = Map.empty

  // Functions
  def handleAmqMessage(json: JValue) = {
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
    val json = ("event" -> "newRobotEncountered") ~ ("robotId" -> event.robotId) ~ ("service" -> "instructionFiller")
    sendToBus(write(json))
  }
}

object InstructionFiller {
  def props = Props[InstructionFiller]
}