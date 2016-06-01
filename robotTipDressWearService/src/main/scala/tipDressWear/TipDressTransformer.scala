package tipDressWear

import core._
import core.Domain._
import akka.actor._
import com.codemettle.reactivemq._
import com.codemettle.reactivemq.ReActiveMQMessages._
import com.codemettle.reactivemq.model._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization.write
import com.github.nscala_time.time.Imports._

/**
  * Created by Henrik on 2016-04-08.
  */

class TipDressTransformer extends ServiceBase {
  // Type aliases
  type RobotName = String
  type NrOfDeviations = Int

  // State
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
      c ! ConsumeFromTopic(topic)
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
        if (!priorEventMap.contains(event.robotId))
          priorEventMap += (event.robotId -> Some(event))
        else {
          val priorEvent = priorEventMap(event.robotId)
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
    if (map.contains(event.robotId))
      result = map
    else
      result = map + (event.robotId -> 0)
    result
  }

  def handleSlopeMap(map: Map[RobotName, Option[Float]], event: TipDressEvent): Map[RobotName, Option[Float]] = {
    var result = Map[RobotName, Option[Float]]()
    if (map.contains(event.robotId))
      result = map
    else
      result = map + (event.robotId -> None)
    result
  }

  def handleCounterMap(map: Map[RobotName, Int], event: TipDressEvent): Map[RobotName, Int] = {
    var result = Map[RobotName, Int]()
    if (map.contains(event.robotId))
      result = map
    else
      result = map + (event.robotId -> 1)
    result
  }

  def reset(event: TipDressEvent) = {
    priorEventMap += (event.robotId -> Some(event))
    warnMap += (event.robotId -> 0)
  }

  def assessWarningNeed(event: TipDressEvent) = {
    val nrOfWarnings: Int = warnMap(event.robotId)
    val warn: Boolean = nrOfWarnings == 3
    if (warn) {
      val warningEvent: TipDressWarningEvent =
        TipDressWarningEvent(event.robotId, event.workCellId, event.robotDataAddress, warn)
      val json = write(warningEvent)
      sendToBus(json)
    }
  }

  def assessRiskOfCutterBreakdown(event: TipDressEvent) = {
    val averageSlope = averageSlopeMap(event.robotId)
    if (averageSlope.isDefined) {
      if(currentSlope > averageSlope.get * 0.8)
        warnMap += (event.robotId -> (warnMap(event.robotId) + 1))
      else
        warnMap += (event.robotId -> 0)
    }
    else
      warnMap += (event.robotId -> 0)
    update(event)
  }

  def update(event: TipDressEvent) = {
    val counter = counterMap(event.robotId)
    if (averageSlopeMap(event.robotId).isEmpty)
      averageSlopeMap += (event.robotId -> Some(currentSlope))
    else {
      averageSlopeMap +=
        (event.robotId -> Some((averageSlopeMap(event.robotId).get * counter + currentSlope) / (counter + 1)))
    }
    priorEventMap += (event.robotId -> Some(event))
    counterMap += (event.robotId -> (counter + 1))
  }

  def differentiate(event1: TipDressEvent, event2: TipDressEvent): Float = {
    (event2.tipDressData.tipDressWear - event1.tipDressData.tipDressWear) /
      event2.tipDressData.eventTime.minus(event1.tipDressData.eventTime.toInstant.millis).toInstant.millis * 1000
  }
}

object TipDressTransformer {
  def props = Props[TipDressTransformer]
}