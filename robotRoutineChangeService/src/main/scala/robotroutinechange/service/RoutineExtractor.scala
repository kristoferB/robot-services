package robotroutinechange.service

import java.text.SimpleDateFormat
import akka.actor._
import com.codemettle.reactivemq._
import com.codemettle.reactivemq.ReActiveMQMessages._
import com.codemettle.reactivemq.model._
import com.typesafe.config.ConfigFactory
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization.write
import com.github.nscala_time.time.Imports._

/**
  * Created by Henrik on 2016-05-10.
  */

class RoutineExtractor extends Actor {
  val customDateFormat = new DefaultFormats {
    override def dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
  }
  implicit val formats = customDateFormat ++ org.json4s.ext.JodaTimeSerializers.all // for json serialization

  // Type aliases
  type RobotName = String
  type Id = String

  // Read from config file
  val config = ConfigFactory.load()
  val address = config.getString("activemq.address")
  val user = config.getString("activemq.user")
  val pass = config.getString("activemq.pass")
  val readFrom = config.getString("service.robotRoutineChange.readFromTopic")
  val writeTo = config.getString("service.robotRoutineChange.writeToTopic")
  val waitRoutines = config.getString("service.robotRoutineChange.listOfWaitRoutines")

  // The state
  var theBus: Option[ActorRef] = None

  // Local variables
  var activityIdMap: Map[RobotName, Map[String, Id]] = Map[RobotName, Map[String, Id]]()
  var priorEventMap: Map[RobotName, Option[PointerChangedEvent]] = Map[RobotName, Option[PointerChangedEvent]]()
  val isStart: Boolean = true
  val jsonWaitRoutines = parse(waitRoutines)
  val listOfWaitRoutines: List[String] = jsonWaitRoutines.extract[List[String]]

  // Functions
  def receive = {
    case "connect" =>
      ReActiveMQExtension(context.system).manager ! GetAuthenticatedConnection(s"nio://$address:61616", user, pass)
    case ConnectionEstablished(request, c) =>
      println("Connected: " + request)
      c ! ConsumeFromTopic(readFrom)
      theBus = Some(c)
    case ConnectionFailed(request, reason) =>
      println("Connection failed: " + reason)
    case mess @ AMQMessage(body, prop, headers) =>
      import Helpers.JValueExtended
      val json = parse(body.toString)
      if (json.has("programPointerPosition") && json.has("instruction") && json.has("isWaiting")) {
        val event: PointerChangedEvent = json.extract[PointerChangedEvent]
        priorEventMap = handlePriorEventMap(priorEventMap, event)
        handleEvent(event)
      } else {
        // do nothing... OR println("Received message of unmanageable type property.")
      }
  }

  def handlePriorEventMap(map: Map[RobotName, Option[PointerChangedEvent]], event: PointerChangedEvent):
  Map[RobotName, Option[PointerChangedEvent]] = {
    var result = Map[RobotName, Option[PointerChangedEvent]]()
    if (map.contains(event.robotId))
      result = map
    else
      result = map + (event.robotId -> None)
    result
  }

  def handleEvent(event: PointerChangedEvent) = {
    val priorEvent = priorEventMap(event.robotId)
    if (priorEvent.isDefined) {
      val priorRoutine: String = priorEvent.get.programPointerPosition.position.routine
      val currentRoutine: String = event.programPointerPosition.position.routine
      if (!priorRoutine.equals(currentRoutine)) {
        var json: String = ""
        activityIdMap = updateActivityIdMap(activityIdMap, event.robotId)
        val priorId = activityIdMap(event.robotId)("prior")
        val currentId = activityIdMap(event.robotId)("current")
        if (!isWaitingRoutine(priorRoutine)) {
          val routineStopEvent =
            RoutineChangedEvent(event.robotId, event.workCellId, priorId, !isStart, priorRoutine,
              event.programPointerPosition.time)
          json = write(routineStopEvent)
          println("PriorRoutine: " + json)
          sendToBus(json)
        }
        if (!isWaitingRoutine(currentRoutine)) {
          val routineStartEvent =
            RoutineChangedEvent(event.robotId, event.workCellId, currentId, isStart, currentRoutine,
              event.programPointerPosition.time)
          json = write(routineStartEvent)
          println("CurrentRoutine: " + json)
          sendToBus(json)
        }
      }
    }
    priorEventMap += (event.robotId -> Some(event))
    activityIdMap += (event.robotId -> Map[String,Id]("current" -> uuid))
  }

  def updateActivityIdMap(map: Map[RobotName, Map[String, Id]], robotId: String): Map[RobotName, Map[String, Id]] = {
    var result = map
    val temp = result(robotId)("current")
    result += (robotId -> Map[String,Id]("current" -> uuid))
    result += (robotId -> Map[String,Id]("prior" -> temp))
    result
  }

  def isWaitingRoutine(routineName: String): Boolean = {
    var flag = false
    if (listOfWaitRoutines.contains(routineName))
      flag = true
    flag
  }

  def sendToBus(json: String) = {
    theBus.foreach{bus => bus ! SendMessage(Topic(writeTo), AMQMessage(json))}
  }

  override def postStop() = {
    theBus.foreach(_ ! CloseConnection)
  }

  def getNow = {
    DateTime.now(DateTimeZone.forID("Europe/Stockholm"))
  }

  def uuid: String = java.util.UUID.randomUUID.toString
}

object RoutineExtractor {
  def props = Props[RoutineExtractor]
}