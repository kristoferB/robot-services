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

  // State
  var isWaiting: Map[RobotId, Option[ActivityId]] = Map.empty

  // Functions
  def handleAmqMessage(json: JValue) = {
    if (json.has("isWaiting"))
      checkIfWaitChange(json)
  }

  def checkIfWaitChange(json: JValue) = {
    val event: PointerWithIsWaiting = json.extract[PointerWithIsWaiting]
    if (isWaiting.contains(event.robotId)) {
      if (isWaiting(event.robotId).isDefined != event.isWaiting) {
        val activityId = if (event.isWaiting) {
          val id = UUID.randomUUID()
          isWaiting += (event.robotId -> Some(id))
          id
        } else {
          val id = isWaiting(event.robotId).get
          isWaiting += (event.robotId -> None)
          id
        }
        val activityEvent = ActivityEvent(activityId.toString, event.isWaiting, event.instruction, event.robotId,
          event.programPointerPosition.time, "wait", event.workCellId)
        println("From waitChange: " + activityEvent)
        sendToBus(write(activityEvent))
      }
    } else {
      if (event.isWaiting)
        isWaiting += (event.robotId -> Some(UUID.randomUUID()))
      else
        isWaiting += (event.robotId -> None)
    }
  }
}

object WaitChange {
  def props = Props[WaitChange]
}