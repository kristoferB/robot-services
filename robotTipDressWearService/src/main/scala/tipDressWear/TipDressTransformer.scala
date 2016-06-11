package tipDressWear

import akka.actor._
import core.ServiceBase
import core.Domain._
import core.Helpers._
import org.json4s._
import org.json4s.native.Serialization.write
import com.github.nscala_time.time.Imports._

/**
  * Created by Henrik on 2016-04-08.
  */

class TipDressTransformer extends ServiceBase {
  // Type aliases
  type RobotName = String
  type NrOfDeviations = Int

  // Variables
  var counterMap: Map[RobotName, Int] = Map.empty
  var currentSlope: Float = 0
  var priorEventMap: Map[RobotName, Option[TipDressEvent]] = Map.empty
  var averageSlopeMap: Map[RobotName, Option[Float]] = Map.empty
  var warnMap: Map[RobotName, NrOfDeviations] = Map.empty

  // Functions
  def handleAmqMessage(json: JValue) = {
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
        else
          reset(event)
      }
      assessWarningNeed(event)
    } else {
      // do nothing... OR log.info("Received message of unmanageable type property.")
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
      val warningEvent = TipDressWarningEvent(event.robotId, event.workCellId, event.address, warn)
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
      event2.tipDressData.time.minus(event1.tipDressData.time.toInstant.millis).toInstant.millis * 1000
  }
}

object TipDressTransformer {
  def props = Props[TipDressTransformer]
}