package cycleStore

import akka.actor._
import com.codemettle.reactivemq.ReActiveMQMessages._
import com.codemettle.reactivemq._
import com.codemettle.reactivemq.model._
import com.github.nscala_time.time.Imports._
import core.ServiceBase
import core.Domain._
import core.Helpers._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization.write
import wabisabi._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util.{Failure, Success}

/**
  * Created by Henrik on 2016-04-15.
  */

class CycleAggregator extends ServiceBase {
  // Type aliases
  type RobotId = String
  type WorkCellId = String
  type ActivityType = String
  type ActivityEvents = List[ActivityEvent]
  type Activities = List[Activity]

  // Elasticsearch
  var elasticClient: Option[Client] = None

  // Maps
  var cycleEventsMap: Map[RobotId, Map[ActivityType, ActivityEvents]] = Map.empty
  var earlyOrLateEventsMap: Map[RobotId, Map[ActivityType, ActivityEvents]] = Map.empty
  var flagMap: Map[WorkCellId, Boolean] = Map.empty
  var workCellMap: Map[WorkCellId, List[RobotId]] = Map.empty
  var workCellStartTimeMap: Map[WorkCellId, DateTime] = Map.empty

  // Functions
  def receive = {
    case "connect" =>
      ReActiveMQExtension(context.system).manager ! GetAuthenticatedConnection(s"nio://$address:61616", user, pass)
      elasticClient = Some(new Client(s"http://$elasticIP:$elasticPort"))
    case ConnectionEstablished(request, c) =>
      println("Connected: " + request)
      c ! ConsumeFromTopic(topic)
      theBus = Some(c)
    case ConnectionFailed(request, reason) =>
      println("Connection failed: " + reason)
    case mess @ AMQMessage(body, prop, headers) =>
      val json = parse(body.toString)
      if (json.has("isStart") && json.has("cycleId")) {
        val event: CycleEvent = json.extract[CycleEvent]
        if (event.isStart) {
          flagMap += (event.workCellId -> true)
          workCellStartTimeMap += (event.workCellId -> event.time)
          handleEarlyEvents(event)
        } else {
          flagMap += (event.workCellId -> false)
          storeCycle(event, mess.properties.messageID)
        }
      } else if (json.has("isStart") && json.has("activityId")) {
        val event: ActivityEvent = json.extract[ActivityEvent]
        updateWorkCellMap(event)
        updateFlagMap(event.workCellId)
        if (flagMap(event.workCellId))
          cycleEventsMap = addToEventMap(cycleEventsMap, event)
        else {
          earlyOrLateEventsMap = addToEventMap(earlyOrLateEventsMap, event)
        }
      } else if (json.has("robotCycleSearchQuery")) {
        val event: RobotCycleSearchQuery = (json \ "robotCycleSearchQuery").extract[RobotCycleSearchQuery]
        retrieveFromES(event)
      } else {
        // do nothing... OR println("Received message of unmanageable type property.")
      }
  }

  def updateWorkCellMap(event: ActivityEvent) = {
    if (workCellMap.contains(event.workCellId)) {
      val workCell = workCellMap(event.workCellId)
      if (workCell.contains(event.robotId)) {
        workCellMap = workCellMap
      } else {
        val newRobotList = workCellMap(event.workCellId) :+ event.robotId
        workCellMap = workCellMap + (event.workCellId -> newRobotList)
      }
    } else
      workCellMap = workCellMap + (event.workCellId -> List[RobotId](event.robotId))
  }

  def updateFlagMap(workCellName: WorkCellId) = {
    if (flagMap.contains(workCellName))
      flagMap = flagMap
    else
      flagMap = flagMap + (workCellName -> false)
  }

  def addToEventMap(map: Map[RobotId, Map[ActivityType, ActivityEvents]], event: ActivityEvent) = {
    var typeToEvents: Map[ActivityType, ActivityEvents] = if (map.contains(event.robotId)) map(event.robotId) else Map.empty
    var events: ActivityEvents = if (typeToEvents.contains(event.`type`)) typeToEvents(event.`type`) else List.empty
    events = events :+ event
    typeToEvents = typeToEvents + (event.`type` -> events)
    map + (event.robotId -> typeToEvents)
  }

  def handleEarlyEvents(startEvent: CycleEvent) = {
    if(workCellMap.contains(startEvent.workCellId)) {
      workCellMap(startEvent.workCellId).foreach { robotId: RobotId =>
        if (earlyOrLateEventsMap.contains(robotId)) {
          earlyOrLateEventsMap(robotId).foreach { case (activityType, activityEvents) =>
            activityEvents.foreach { activityEvent =>
              if (activityEvent.time.isAfter(startEvent.time))
                cycleEventsMap = addToEventMap(cycleEventsMap, activityEvent)
            }
          }
          earlyOrLateEventsMap = earlyOrLateEventsMap + (robotId -> Map.empty)
        }
      }
    }
  }

  def storeCycle(cycleStop: CycleEvent, elasticId: Option[String]) = {
    if(workCellStartTimeMap.contains(cycleStop.workCellId) && workCellMap.contains(cycleStop.workCellId)) {
      val startTime = workCellStartTimeMap(cycleStop.workCellId)
      val workCellRobotIds = workCellMap(cycleStop.workCellId)
      var workCellEvents = cycleEventsMap.filterKeys(workCellRobotIds.contains(_))
      cycleEventsMap = cycleEventsMap.filterKeys(!workCellRobotIds.contains(_))

      // waits, asynchronously, for pointer changes which may arrive after cycle stop even though they should not
      val asyncWait: Future[Unit] = Future { Thread.sleep(5000) }
      asyncWait onSuccess {
        case _ =>
          workCellRobotIds.foreach { robotId =>
            if (earlyOrLateEventsMap.contains(robotId)) {
              earlyOrLateEventsMap(robotId).foreach { case (eventType, events) =>
                events.foreach { event =>
                  if (event.time.isBefore(cycleStop.time))
                    workCellEvents = addToEventMap(workCellEvents, event)
                }
              }
              earlyOrLateEventsMap += (robotId -> Map.empty)
            }
          }
          val activities = foldToActivities(workCellEvents)

          if (allRobotsInCycle(cycleStop.workCellId, activities)) {
            val workCellCycle = WorkCellCycle(cycleStop.workCellId, uuid, startTime, cycleStop.time, activities)
            val json = write(workCellCycle)
            sendToES(json, elasticId)
          }
      }
    }
  }

  def foldToActivities(workCellEvents: Map[RobotId, Map[ActivityType, ActivityEvents]]): Map[RobotId, Map[ActivityType, Activities]] = {

    def helperFunction(activityEvents: Option[ActivityEvents]): Option[Activities] = activityEvents match {
      case Some(Nil) =>
        Some(List.empty[Activity])
      case Some(ae1 :: ae2 :: aes) =>
        if (ae1.isStart && !ae2.isStart)  {
          val activity = Activity(ae1.activityId, ae1.time, ae1.name, ae2.time, ae1.`type`)
          val rest = helperFunction(Some(aes))
          if (rest.isDefined)
            Some(activity :: rest.get)
          else
            None
        } else
          None
      case _ => None
    }

    workCellEvents.map { case (robotId, robotEventTypes) =>
      (robotId, robotEventTypes.map { case (eventType, events) =>
        val activities = helperFunction(Some(events))
        if (activities.isDefined)
          (eventType, activities.get)
        else
          (eventType, List.empty)
      })
    }

  }

  def allRobotsInCycle(workCellId: WorkCellId, activities: Map[RobotId, _]): Boolean = {
    val robotsInCycle = activities.keySet
    val robotsInWorkCell = workCellMap(workCellId).toSet
    if (robotsInWorkCell.diff(robotsInCycle).isEmpty)
      true
    else
      false
  }

  def sendToES(json: String, elasticId: Option[String]) = {
    elasticClient.foreach{client => client.index(
      index = "robot-cycle-store", `type` = "cycles", id = elasticId,
      data = json, refresh = true
    )}
  }

  def retrieveFromES(event: RobotCycleSearchQuery) = {
    var jsonQuery: Option[String] = None
    if (event.cycleId.isDefined) {
      jsonQuery = Some("{ \"size\" : 20, \"query\": { \"match\" : { \"entryId\" : \"" +
        s"${event.cycleId.get}" +
        "\" } } }")
    } else if (event.timeSpan.isDefined) {
      jsonQuery = Some("{ \"size\" : 20, \"query\": { \"bool\" :{ \"must\" : [ { \"term\" : { \"workCellId\" : \"" +
        s"${event.workCellId}" +
        "\" } },{ \"range\" : { \"from\" : { \"gte\" : \"" +
        s"${event.timeSpan.get.start}" +
        "\" } } }, { \"range\" : { \"to\" : { \"lte\" : \"" +
        s"${event.timeSpan.get.stop}" +
        "\" } } } ] } } }")
    }
    if (jsonQuery.isDefined) {
      elasticClient.foreach{client =>
        val searchResponse: Future[String] =
          client.search(index = "robot-cycle-store", query = jsonQuery.get).map(_.getResponseBody)
        searchResponse onComplete {
          case Failure(e) => println("An error has occurred while retrieving cycles from elastic: " + e.getMessage)
          case Success(cycles) =>
            val json = parse(cycles)
            val hits: Int = (json \ "hits" \ "total").extract[Int]
            if (hits == 1) {
              val extractedCycles = (json \ "hits" \ "hits" \ "_source").extract[WorkCellCycle]
              val robotCyclesResponse = RobotCyclesResponse(s"${event.workCellId}", List[WorkCellCycle](extractedCycles))
              val jsonResponse = write(Map[String, RobotCyclesResponse]("robotCyclesResponse" -> robotCyclesResponse))
              sendToBus(jsonResponse)
            } else {
              val extractedCycles = (json \ "hits" \ "hits" \ "_source").extract[List[WorkCellCycle]]
              val robotCyclesResponse = RobotCyclesResponse(s"${event.workCellId}", extractedCycles)
              val jsonResponse = write(Map[String, RobotCyclesResponse]("robotCyclesResponse" -> robotCyclesResponse))
              sendToBus(jsonResponse)
            }
        }
      }
    } else {
      val emptyResponse = RobotCyclesResponse(s"${event.workCellId}", List.empty[WorkCellCycle])
      val jsonResponse = write(Map[String, RobotCyclesResponse]("robotCyclesResponse" -> emptyResponse))
      sendToBus(jsonResponse)
    }
  }

  def uuid: String = java.util.UUID.randomUUID.toString
}

object CycleAggregator {
  def props = Props[CycleAggregator]
}