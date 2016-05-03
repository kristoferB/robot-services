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
  type RobotId = String
  type PointerChanges = List[PointerChangedEvent]

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
  var cycleEventsMap: Map[RobotId, PointerChanges] = Map[RobotId, PointerChanges]()
  var earlyEventsMap: Map[RobotId, PointerChanges] = Map[RobotId, PointerChanges]()
  var lateEventsMap: Map[RobotId, PointerChanges] = Map[RobotId, PointerChanges]()
  var flagMap: Map[RobotId, Boolean] = Map[RobotId, Boolean]()

  // Functions
  def receive = {
    case "connect" =>
      ReActiveMQExtension(context.system).manager ! GetAuthenticatedConnection(s"nio://$address:61616", user, pass)
      elasticClient = Some(new Client(s"http://$elasticIP:$elasticPort"))
    case ConnectionEstablished(request, c) =>
      println("Connected: " + request)
      c ! ConsumeFromTopic(readFrom) // change to writeTo to be able to utilize the testMessageSender actor
      theBus = Some(c)
    case ConnectionFailed(request, reason) =>
      println("Connection failed: " + reason)
    case mess @ AMQMessage(body, prop, headers) =>
      import Helpers.JValueExtended
      val json = parse(body.toString)
      if (json.has("cycleStart")) {
        val event: CycleStartEvent = json.extract[CycleStartEvent]
        flagMap += (event.robotId -> true)
        handleEarlyEvents(event)
      } else if (json.has("cycleStop")) {
        val event: CycleStopEvent = json.extract[CycleStopEvent]
        flagMap += (event.robotId -> false)
        storeCycle(event, mess.properties.messageID)
      } else if (json.has("programPointerPosition") & json.has("isWaiting")) {
        val event: PointerChangedEvent = json.extract[PointerChangedEvent]
        flagMap = handleFlagMap(flagMap,event.robotId)
        if (flagMap(event.robotId)) {
          cycleEventsMap = handleEventsMap(cycleEventsMap, event)
        }
        else {
          earlyEventsMap = handleEventsMap(earlyEventsMap, event)
          lateEventsMap = handleEventsMap(lateEventsMap, event)
        }
      } else {
        // do nothing... OR println("Received message of unmanageable type property.")
      }
  }

  def handleFlagMap(map: Map[RobotId, Boolean], robotId: RobotId): Map[RobotId, Boolean] = {
    var result = Map[RobotId, Boolean]()
    if (map.contains(robotId))
      result = map
    else
      result = map + (robotId -> false)
    result
  }

  def handleEventsMap(map: Map[RobotId, PointerChanges], event: PointerChangedEvent): Map[RobotId, PointerChanges] = {
    var result = Map[RobotId, PointerChanges]()
    if (map.contains(event.robotId)) {
      val newList: PointerChanges = map(event.robotId) :+ event
      result = map + (event.robotId -> newList)
    } else
      result = map + (event.robotId -> List[PointerChangedEvent](event))
    result
  }

  def handleEarlyEvents(startEvent: CycleStartEvent) = {
    var unHandledEvents: PointerChanges = List[PointerChangedEvent]()
    if (earlyEventsMap.contains(startEvent.robotId)) {
      earlyEventsMap(startEvent.robotId).foreach(event => {
        if (event.programPointerPosition.eventTime.isAfter(startEvent.cycleStart))
          unHandledEvents = unHandledEvents :+ event
      })
      earlyEventsMap += (startEvent.robotId -> List.empty[PointerChangedEvent])
      if (cycleEventsMap.contains(startEvent.robotId))
        cycleEventsMap += (startEvent.robotId -> (unHandledEvents ::: cycleEventsMap(startEvent.robotId)))
      else
        cycleEventsMap += (startEvent.robotId -> unHandledEvents)
    }
  }

  def storeCycle(stopEvent: CycleStopEvent, elasticId: Option[String]) = {
    if (cycleEventsMap.contains(stopEvent.robotId)) {
      var unHandledEvents: PointerChanges = List[PointerChangedEvent]()
      val localCycleEvents: PointerChanges = cycleEventsMap(stopEvent.robotId)
      cycleEventsMap += (stopEvent.robotId -> List.empty[PointerChangedEvent])
      val latestEventTime: DateTime = localCycleEvents.last.programPointerPosition.eventTime
      // waits, asynchronously, for pointer changes which may arrive after cycle stop even though they should not
      val asyncWait: Future[Unit] = Future { Thread.sleep(5000) }
      asyncWait onSuccess {
        case _ =>
          if (lateEventsMap.contains(stopEvent.robotId)) {
            val localLateEvents: PointerChanges = lateEventsMap(stopEvent.robotId)
            lateEventsMap += (stopEvent.robotId -> List.empty[PointerChangedEvent])
            localLateEvents.foreach(event => {
              val eventTime: DateTime = event.programPointerPosition.eventTime
              if (eventTime.isAfter(latestEventTime) & eventTime.isBefore(stopEvent.cycleStop))
                unHandledEvents = unHandledEvents :+ event
            })
          }
          val cycle: PointerChanges = localCycleEvents ::: unHandledEvents
          val json = write(Map[String, PointerChanges]("Cycle" -> cycle))
          sendToES(json, stopEvent.robotId, elasticId)
      }
    }
  }

  def sendToBus(json: String) = {
    theBus.foreach{bus => bus ! SendMessage(Topic(writeTo), AMQMessage(json))}
  }

  def sendToES(json: String, robotId: RobotId, elasticId: Option[String]) = {
    elasticClient.foreach{client => client.index(
      index = "Cycles.".concat(robotId).toLowerCase, `type` = "cycle", id = elasticId,
      data = json, refresh = true
    )}
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