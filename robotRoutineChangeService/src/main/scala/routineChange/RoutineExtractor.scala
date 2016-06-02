package routineChange

import akka.actor._
import com.codemettle.reactivemq._
import com.codemettle.reactivemq.ReActiveMQMessages._
import com.codemettle.reactivemq.model._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization.write
import core.ServiceBase
import core.Domain._
import core.Helpers._

/**
  * Created by Henrik on 2016-05-10.
  */

class RoutineExtractor extends ServiceBase {
  // Type aliases
  type RobotName = String
  type Id = String

  // Config file
  val waitRoutines = config.getString("services.routineChange.waitRoutines")

  // Variables
  var activityIdMap: Map[RobotName, Map[String, Id]] = Map[RobotName, Map[String, Id]]()
  var priorEventMap: Map[RobotName, PointerChangedEvent] = Map[RobotName, PointerChangedEvent]()
  val isStart: Boolean = true
  val jsonWaitRoutines = parse(waitRoutines)
  val listOfWaitRoutines: List[String] = jsonWaitRoutines.extract[List[String]]

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
      if (json.has("programPointerPosition") && !json.has("instruction")) {
        val event: PointerChangedEvent = json.extract[PointerChangedEvent]
        activityIdMap = handleActivityIdMap(activityIdMap, event)
        handleEvent(event)
      } else {
        // do nothing... OR println("Received message of unmanageable type property.")
      }
  }

  def handleActivityIdMap(map: Map[RobotName, Map[String, Id]], event: PointerChangedEvent):
  Map[RobotName, Map[String, Id]] = {
    var result = Map[RobotName, Map[String, Id]]()
    if (map.contains(event.robotId))
      result = map
    else
      result = map + (event.robotId -> Map[String, Id]("current" -> uuid))
    result
  }

  def handleEvent(event: PointerChangedEvent) = {
    if (priorEventMap.contains(event.robotId)) {
      val priorEvent = priorEventMap(event.robotId)
      val priorRoutine: String = priorEvent.programPointerPosition.position.routine
      val currentRoutine: String = event.programPointerPosition.position.routine
      if (!priorRoutine.equals(currentRoutine)) {
        var json: String = ""
        activityIdMap = updateActivityIdMap(activityIdMap, event.robotId)
        val priorId = activityIdMap(event.robotId)("prior")
        val currentId = activityIdMap(event.robotId)("current")
        if (!isWaitingRoutine(priorRoutine)) {
          val routineStopEvent =
            ActivityEvent(priorId, !isStart, priorRoutine, event.robotId, event.programPointerPosition.time,
              "routines", event.workCellId)
          json = write(routineStopEvent)
          println("Previous routine: " + json)
          sendToBus(json)
        }
        if (!isWaitingRoutine(currentRoutine)) {
          val routineStartEvent =
            ActivityEvent(currentId, isStart, currentRoutine, event.robotId, event.programPointerPosition.time,
              "routines", event.workCellId)
          json = write(routineStartEvent)
          println("Current routine: " + json)
          sendToBus(json)
        }
      }
    }
    priorEventMap += (event.robotId -> event)
  }

  def updateActivityIdMap(map: Map[RobotName, Map[String, Id]], robotId: String): Map[RobotName, Map[String, Id]] = {
    var result = map
    val temp = result(robotId)("current")
    result += (robotId -> Map[String,Id]("current" -> uuid, "prior" -> temp))
    result
  }

  def isWaitingRoutine(routineName: String): Boolean = {
    var flag = false
    if (listOfWaitRoutines.contains(routineName))
      flag = true
    flag
  }

  def uuid: String = java.util.UUID.randomUUID.toString
}

object RoutineExtractor {
  def props = Props[RoutineExtractor]
}