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
  type RobotId = String

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
  var currentSlope: Float = 0
  var priorEvent: TipDressEvent = null
  var averageSlopeMap: Map[RobotId, Option[Float]] = Map[RobotId, Option[Float]]()
  var warnMap: Map[RobotId, Boolean] = Map[RobotId, Boolean]()

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
        if (priorEvent == null)
          priorEvent = event
        else {
          currentSlope = differentiate(priorEvent, event)
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

  def handleWarnMap(map: Map[RobotId, Boolean], event: TipDressEvent): Map[RobotId, Boolean] = {
    var result = Map[RobotId, Boolean]()
    if (map.contains(event.robotId))
      result = map
    else
      result = map + (event.robotId -> false)
    result
  }

  def handleSlopeMap(map: Map[RobotId, Option[Float]], event: TipDressEvent): Map[RobotId, Option[Float]] = {
    var result = Map[RobotId, Option[Float]]()
    if (map.contains(event.robotId))
      result = map
    else
      result = map + (event.robotId -> None)
    result
  }

  def reset(event: TipDressEvent) = {
    priorEvent = event
    averageSlopeMap += (event.robotId -> None)
    warnMap += (event.robotId -> false)
  }

  def assessWarningNeed(event: TipDressEvent) = {
    val warn = warnMap(event.robotId)
    if (warn) {
      val warningEvent: TipDressWarningEvent = TipDressWarningEvent(event.robotId, event.robotDataAddress, warn)
      val json = write(warningEvent)
      sendToBus(json)
    }
  }

  def assessRiskOfCutterBreakdown(event: TipDressEvent) = {
    val averageSlope = averageSlopeMap(event.robotId)
    if (averageSlope.isDefined) {
      if(currentSlope > averageSlope.get * 0.8)
        warnMap += (event.robotId -> true)
      else
        warnMap += (event.robotId -> false)
    }
    else
      warnMap += (event.robotId -> false)
    update(event)
  }

  def update(event: TipDressEvent) = {
    if (averageSlopeMap(event.robotId).isEmpty)
      averageSlopeMap += (event.robotId -> Some(currentSlope))
    else
      averageSlopeMap += (event.robotId -> Some((averageSlopeMap(event.robotId).get + currentSlope) / 2))
    priorEvent = event
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