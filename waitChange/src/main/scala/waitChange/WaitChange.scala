package waitChange

import akka.actor._
import core.Domain._
import core.Helpers._
import core.ServiceBase
import java.util.UUID
import org.json4s._
import org.json4s.native.Serialization.write

/**
  * Created by Daniel on 2016-06-02.
  */

/*
 * Emits activity events when a robot toggles wait.
 */

class WaitChange extends ServiceBase {
  // Type aliases
  type RobotId = String
  type ActivityId = UUID
  type WaitInstruction = String

  // State
  var isWaiting: Map[RobotId, Option[(ActivityId, WaitInstruction)]] = Map.empty

  // Functions
  def handleAmqMessage(json: JValue) = {
    if (json.has("isWaiting"))
      checkIfWaitChange(json)
  }

  def checkIfWaitChange(json: JValue) = {
    val event: PointerWithIsWaiting = json.extract[PointerWithIsWaiting]

    if (!isWaiting.contains(event.robotId)) {
      isWaiting += (event.robotId -> None)
    }

    if (isWaiting(event.robotId).isDefined && !event.isWaiting) {
      val (activityId, waitInstruction): (ActivityId, WaitInstruction) = if (event.isWaiting) {
        val id = UUID.randomUUID()
        isWaiting += (event.robotId -> Some((id, event.instruction)))
        (id, event.instruction)
      } else {
        val (id, instruction) = isWaiting(event.robotId).get
        isWaiting += (event.robotId -> None)
        (id, instruction)
      }
      val activityEvent = ActivityEvent(activityId.toString, event.isWaiting, waitInstruction, event.robotId,
        event.programPointerPosition.time, "wait", event.workCellId)
      log.info("From waitChange: " + activityEvent)
      sendToBus(write(activityEvent))
    }

  }
}

object WaitChange {
  def props = Props[WaitChange]
}