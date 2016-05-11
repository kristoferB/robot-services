package robotroutinechange.service

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
  implicit val formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all // for json serialization

  // Type aliases
  type RobotName = String

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
  var priorEventMap: Map[RobotName, Option[PointerChangedEvent]] = Map[RobotName, Option[PointerChangedEvent]]()
  val startFlag: Boolean = true
  val jsonWaitRoutines = parse(waitRoutines)
  val listOfWaitRoutines: List[String] = jsonWaitRoutines.extract[List[String]]

  // Functions
  def receive = {
    case "connect" =>
      ReActiveMQExtension(context.system).manager ! GetAuthenticatedConnection(s"nio://$address:61616", user, pass)
    case ConnectionEstablished(request, c) =>
      println("Connected: " + request)
      c ! ConsumeFromTopic(readFrom) // change to writeTo to be able to utilize the testMessageSender actor
      theBus = Some(c)
    case ConnectionFailed(request, reason) =>
      println("Connection failed: " + reason)
    case mess @ AMQMessage(body, prop, headers) =>
      import Helpers.JValueExtended
      val json = parse(body.toString)
      if (json.has("programPointerPosition") & json.has("instruction") & json.has("isWaiting")) {
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
    if (map.contains(event.robotName))
      result = map
    else
      result = map + (event.robotName -> None)
    result
  }

  def handleEvent(event: PointerChangedEvent) = {
    val priorEvent = priorEventMap(event.robotName)
    if (priorEvent.isDefined) {
      val priorRoutine: String = priorEvent.get.programPointerPosition.position.routineName
      val currentRoutine: String = event.programPointerPosition.position.routineName
      if (!priorRoutine.equals(currentRoutine)) {
        var json: String = ""
        if (!isWaitingRoutine(priorRoutine)) {
          val routineStopEvent =
            RoutineChangedEvent(event.robotName, "workcell", !startFlag, priorRoutine, event.programPointerPosition.eventTime)
          json = write(routineStopEvent)
          sendToBus(json)
        }
        if (!isWaitingRoutine(currentRoutine)) {
          val routineStartEvent =
            RoutineChangedEvent(event.robotName, "workcell", startFlag, currentRoutine, event.programPointerPosition.eventTime)
          json = write(routineStartEvent)
          sendToBus(json)
        }
      }
    }
    priorEventMap += (event.robotName -> Some(event))
  }

  def isWaitingRoutine(routineName: String): Boolean = {
    var flag = false
    if (listOfWaitRoutines.contains(routineName)) {
      flag = true
    }
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
}

object RoutineExtractor {
  def props = Props[RoutineExtractor]
}