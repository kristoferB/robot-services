package robottipdresswear.service

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
  * Created by Henrik on 2016-04-08.
  */

class TipDressTransformer extends Actor {
  implicit val formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all // for json serialization

  // Type aliases
  type RobotName = String
  type NrOfDeviations = Int

  // Read from config file
  val config = ConfigFactory.load()
  val address = config.getString("activemq.address")
  val user = config.getString("activemq.user")
  val pass = config.getString("activemq.pass")
  val readFrom = config.getString("service.robotTipDressWear.readFromTopic")
  val writeTo = config.getString("service.robotTipDressWear.writeToTopic")

  // The state
  var theBus: Option[ActorRef] = None

  // Local variables
  //var counter: Int = 0
  var counterMap: Map[RobotName, Int] = Map[RobotName, Int]()
  var currentSlope: Float = 0
  //var priorEvent: Option[TipDressEvent] = None
  var priorEventMap: Map[RobotName, Option[TipDressEvent]] = Map[RobotName, Option[TipDressEvent]]()
  var averageSlopeMap: Map[RobotName, Option[Float]] = Map[RobotName, Option[Float]]()
  var warnMap: Map[RobotName, NrOfDeviations] = Map[RobotName, NrOfDeviations]()

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
      if (json.has("tipDressData")) {
        val event: TipDressEvent = json.extract[TipDressEvent]
        warnMap = handleWarnMap(warnMap, event)
        averageSlopeMap = handleSlopeMap(averageSlopeMap, event)
        counterMap = handleCounterMap(counterMap, event)
        if (!priorEventMap.contains(event.robotName))
          priorEventMap += (event.robotName -> Some(event))
        else {
          val priorEvent = priorEventMap(event.robotName)
          currentSlope = differentiate(priorEvent.get, event)
          if (currentSlope <= 0)
            assessRiskOfCutterBreakdown(event)
          else {
            reset(event)
          }
        }
        assessWarningNeed(event)
      } else {
        // do nothing... OR println("Received message of unmanageable type property.")
      }
  }

  def handleWarnMap(map: Map[RobotName, NrOfDeviations], event: TipDressEvent): Map[RobotName, NrOfDeviations] = {
    var result = Map[RobotName, NrOfDeviations]()
    if (map.contains(event.robotName))
      result = map
    else
      result = map + (event.robotName -> 0)
    result
  }

  def handleSlopeMap(map: Map[RobotName, Option[Float]], event: TipDressEvent): Map[RobotName, Option[Float]] = {
    var result = Map[RobotName, Option[Float]]()
    if (map.contains(event.robotName))
      result = map
    else
      result = map + (event.robotName -> None)
    result
  }

  def handleCounterMap(map: Map[RobotName, Int], event: TipDressEvent): Map[RobotName, Int] = {
    var result = Map[RobotName, Int]()
    if (map.contains(event.robotName))
      result = map
    else
      result = map + (event.robotName -> 1)
    result
  }

  def reset(event: TipDressEvent) = {
    priorEventMap += (event.robotName -> Some(event))
    warnMap += (event.robotName -> 0)
  }

  def assessWarningNeed(event: TipDressEvent) = {
    val nrOfWarnings: Int = warnMap(event.robotName)
    val warn: Boolean = nrOfWarnings == 3
    if (warn) {
      val warningEvent: TipDressWarningEvent = TipDressWarningEvent(event.robotName, event.robotDataAddress, warn)
      val json = write(warningEvent)
      sendToBus(json)
    }
  }

  def assessRiskOfCutterBreakdown(event: TipDressEvent) = {
    val averageSlope = averageSlopeMap(event.robotName)
    if (averageSlope.isDefined) {
      if(currentSlope > averageSlope.get * 0.8)
        warnMap += (event.robotName -> (warnMap(event.robotName) + 1))
      else
        warnMap += (event.robotName -> 0)
    }
    else
      warnMap += (event.robotName -> 0)
    update(event)
  }

  def update(event: TipDressEvent) = {
    val counter = counterMap(event.robotName)
    if (averageSlopeMap(event.robotName).isEmpty)
      averageSlopeMap += (event.robotName -> Some(currentSlope))
    else {
      averageSlopeMap +=
        (event.robotName -> Some((averageSlopeMap(event.robotName).get * counter + currentSlope) / (counter + 1)))
    }
    priorEventMap += (event.robotName -> Some(event))
    counterMap += (event.robotName -> (counter + 1))
  }

  def differentiate(event1: TipDressEvent, event2: TipDressEvent): Float = {
    (event2.tipDressData.tipDressWear - event1.tipDressData.tipDressWear) /
      event2.tipDressData.eventTime.minus(event1.tipDressData.eventTime.toInstant.millis).toInstant.millis * 1000
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

object TipDressTransformer {
  def props = Props[TipDressTransformer]
}