package robotcyclestore.service

import akka.actor._
import com.codemettle.reactivemq._
import com.codemettle.reactivemq.ReActiveMQMessages._
import com.codemettle.reactivemq.model._
import com.typesafe.config.ConfigFactory
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization.write
import com.github.nscala_time.time.Imports._
import wabisabi._
import scala.concurrent._
import ExecutionContext.Implicits.global

/**
  * Created by Henrik on 2016-04-15.
  */

class CycleAggregator extends Actor {
  implicit val formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all // for json serialization

  // Type aliases
  type RobotName = String
  type WorkCellName = String
  type RoutineChanges = List[RoutineChangedEvent]

  // Read from config file
  val config = ConfigFactory.load()
  val address = config.getString("activemq.address")
  val user = config.getString("activemq.user")
  val pass = config.getString("activemq.pass")
  val readFrom = config.getString("service.robotCycleStore.readFromTopic")
  val writeTo = config.getString("service.robotCycleStore.writeToTopic")
  val elasticIP = config.getString("elastic.ip")
  val elasticPort = config.getString("elastic.port")

  // The state
  var theBus: Option[ActorRef] = None

  // Elasticsearch
  var elasticClient: Option[Client] = None

  // Local variables
  var cycleEventsMap: Map[RobotName, RoutineChanges] = Map[RobotName, RoutineChanges]()
  var earlyEventsMap: Map[RobotName, RoutineChanges] = Map[RobotName, RoutineChanges]()
  var lateEventsMap: Map[RobotName, RoutineChanges] = Map[RobotName, RoutineChanges]()
  var flagMap: Map[WorkCellName, Boolean] = Map[WorkCellName, Boolean]()
  var workCellMap: Map[WorkCellName, List[RobotName]] = Map[WorkCellName, List[RobotName]]()
  var workCellStartTimeMap: Map[WorkCellName, DateTime] = Map[WorkCellName, DateTime]()

  // Functions
  def receive = {
    case "connect" =>
      ReActiveMQExtension(context.system).manager ! GetAuthenticatedConnection(s"nio://$address:61616", user, pass)
      elasticClient = Some(new Client(s"http://$elasticIP:$elasticPort"))
    case ConnectionEstablished(request, c) =>
      println("Connected: " + request)
      c ! ConsumeFromTopic(readFrom)
      theBus = Some(c)
    case ConnectionFailed(request, reason) =>
      println("Connection failed: " + reason)
    case mess @ AMQMessage(body, prop, headers) =>
      import Helpers.JValueExtended
      val json = parse(body.toString)
      if (json.has("cycleStart")) {
        val event: CycleStartEvent = json.extract[CycleStartEvent]
        flagMap += (event.workCellName -> true)
        workCellStartTimeMap += (event.workCellName -> event.cycleStart)
        handleEarlyEvents(event)
      } else if (json.has("cycleStop")) {
        val event: CycleStopEvent = json.extract[CycleStopEvent]
        flagMap += (event.workCellName -> false)
        storeCycle(event, mess.properties.messageID)
      } else if (json.has("startFlag") && json.has("routineName")) {
        val event: RoutineChangedEvent = json.extract[RoutineChangedEvent]
        workCellMap = handleWorkCellMap(workCellMap, event)
        flagMap = handleFlagMap(flagMap, event.workCellName)
        if (flagMap(event.workCellName)) {
          cycleEventsMap = handleEventsMap(cycleEventsMap, event)
        }
        else {
          earlyEventsMap = handleEventsMap(earlyEventsMap, event)
          lateEventsMap = handleEventsMap(lateEventsMap, event)
        }
      } else if (json.has("robotCycleSearchQuery")) {
        println("Not implemented yet...")
      } else {
        // do nothing... OR println("Received message of unmanageable type property.")
      }
  }

  def handleWorkCellMap(map: Map[WorkCellName, List[RobotName]], event: RoutineChangedEvent):
  Map[WorkCellName, List[RobotName]] = {
    var result = Map[WorkCellName, List[RobotName]]()
    if (map.contains(event.workCellName)) {
      if (map(event.workCellName).contains(event.robotName))
        result = map
      else {
        val newRobotList = map(event.workCellName) :+ event.robotName
        result = map + (event.workCellName -> newRobotList)
      }
    } else {
      result = map + (event.workCellName -> List[RobotName](event.robotName))
    }
    result
  }

  def handleFlagMap(map: Map[WorkCellName, Boolean], workCellName: WorkCellName): Map[WorkCellName, Boolean] = {
    var result = Map[WorkCellName, Boolean]()
    if (map.contains(workCellName))
      result = map
    else
      result = map + (workCellName -> false)
    result
  }

  def handleEventsMap(map: Map[RobotName, RoutineChanges], event: RoutineChangedEvent): Map[RobotName, RoutineChanges] = {
    var result = Map[RobotName, RoutineChanges]()
    if (map.contains(event.robotName)) {
      val newList: RoutineChanges = map(event.robotName) :+ event
      result = map + (event.robotName -> newList)
    } else
      result = map + (event.robotName -> List[RoutineChangedEvent](event))
    result
  }

  def handleEarlyEvents(startEvent: CycleStartEvent) = {
    var unHandledEvents: RoutineChanges = List[RoutineChangedEvent]()
    if(workCellMap.contains(startEvent.workCellName)) {
      workCellMap(startEvent.workCellName).foreach{robotName: RobotName =>
        if (earlyEventsMap.contains(robotName)) {
          earlyEventsMap(robotName).foreach{event =>
            if (event.eventTime.isAfter(startEvent.cycleStart))
              unHandledEvents = unHandledEvents :+ event
          }
          earlyEventsMap += (robotName -> List.empty[RoutineChangedEvent])
          if (cycleEventsMap.contains(robotName))
            cycleEventsMap += (robotName -> (unHandledEvents ::: cycleEventsMap(robotName)))
          else
            cycleEventsMap += (robotName -> unHandledEvents)
        }
      }
    }
  }

  def storeCycle(stopEvent: CycleStopEvent, elasticId: Option[String]) = {
    if(workCellStartTimeMap.contains(stopEvent.workCellName) && workCellMap.contains(stopEvent.workCellName)) {
      var activities: Map[RobotName, List[Routine]] = Map[RobotName, List[Routine]]()
      val startTime = workCellStartTimeMap(stopEvent.workCellName)
      workCellMap(stopEvent.workCellName).foreach{robotName: RobotName =>
        if (cycleEventsMap.contains(robotName)) {
          var unHandledEvents: RoutineChanges = List[RoutineChangedEvent]()
          val localCycleEvents: RoutineChanges = cycleEventsMap(robotName)
          cycleEventsMap += (robotName -> List.empty[RoutineChangedEvent])
          val latestEventTime: DateTime = localCycleEvents.last.eventTime
          // waits, asynchronously, for pointer changes which may arrive after cycle stop even though they should not
          val asyncWait: Future[Unit] = Future { Thread.sleep(5000) }
          asyncWait onSuccess {
            case _ =>
              if (lateEventsMap.contains(robotName)) {
                val localLateEvents: RoutineChanges = lateEventsMap(robotName)
                lateEventsMap += (robotName -> List.empty[RoutineChangedEvent])
                localLateEvents.foreach{event =>
                  val eventTime: DateTime = event.eventTime
                  if (eventTime.isAfter(latestEventTime) && eventTime.isBefore(stopEvent.cycleStop))
                    unHandledEvents = unHandledEvents :+ event
                }
              }
              val cycle: RoutineChanges = localCycleEvents ::: unHandledEvents
              val packagedCycle: Option[List[Routine]] = packRoutines(cycle)
              if (packagedCycle.isDefined)
                activities += (robotName ->  packagedCycle.get)
          }
        }
      }
      if (allRobotsInCycle(stopEvent.workCellName, activities)) {
        val workCellCycle = WorkCellCycle(stopEvent.workCellName, startTime, stopEvent.cycleStop, activities)
        val json = write(workCellCycle)
        sendToES(json, elasticId)
      }
    }
  }

  def packRoutines(cycle: RoutineChanges): Option[List[Routine]] = {
    def helperFunction(routines: Option[RoutineChanges]): Option[List[Routine]] = routines match {
      case Some(Nil) =>
        Some(List.empty[Routine])
      case Some(r1 :: r2 :: rs) =>
        if (r1.startFlag && !r2.startFlag)  {
          val activity = Routine(r1.routineName, r1.eventTime, r2.eventTime)
          val rest = helperFunction(Some(rs))
          if (rest.isDefined)
            Some(activity :: rest.get)
          else
            None
        } else
          None
      case _ => None
    }
    val combinedRoutines: Option[List[Routine]] = helperFunction(Some(cycle))
    combinedRoutines
  }

  def allRobotsInCycle(workCellName: WorkCellName, activities: Map[RobotName, List[Routine]]): Boolean = {
    val robotsInCycle = activities.keySet
    val robotsInWorkCell = workCellMap(workCellName).toSet
    if (robotsInWorkCell.diff(robotsInCycle).isEmpty)
      true
    else
      false
  }

  def sendToBus(json: String) = {
    theBus.foreach{bus => bus ! SendMessage(Topic(writeTo), AMQMessage(json))}
  }

  def sendToES(json: String, elasticId: Option[String]) = {
    elasticClient.foreach{client => client.index(
      index = "robot-cycle-store", `type` = "cycles", id = elasticId,
      data = json, refresh = true
    )}
  }

  def getFromES(json: String): String = {
    println("Not implemented yet...")
    val json = write("Not implemented yet...")
    json
  }

  override def postStop() = {
    theBus.foreach(_ ! CloseConnection)
    Client.shutdown()
  }

  def getNow = {
    DateTime.now(DateTimeZone.forID("Europe/Stockholm"))
  }
}

object CycleAggregator {
  def props = Props[CycleAggregator]
}