package cycleChange

import akka.actor._
import com.codemettle.reactivemq.ReActiveMQMessages._
import com.codemettle.reactivemq._
import com.codemettle.reactivemq.model._
import core.Domain._
import core.Helpers._
import core.ServiceBase
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization.write

/**
  * Created by Henrik on 2016-06-02.
  */

class CycleChange extends ServiceBase {
  // Type aliases
  type Id = String
  type Instruction = String
  type WorkCellId = String

  // Variables
  var cycleIdMap: Map[WorkCellId, Id] = Map[WorkCellId,Id]()

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
      if (json.has("newSignalState")) {
        val event: IncomingCycleEvent = json.extract[IncomingCycleEvent]
        cycleIdMap = handleCycleIdMap(cycleIdMap, event)
        convert(event)
      } else {
        // do nothing... OR println("Received message of unmanageable type property.")
      }
  }

  def handleCycleIdMap(map: Map[WorkCellId, Id], event: IncomingCycleEvent): Map[WorkCellId, Id] = {
    var result = Map[WorkCellId, Id]()
    if (map.contains(event.workCellId))
      result = map
    else
      result = map + (event.workCellId -> uuid)
    result
  }

  def convert(event: IncomingCycleEvent) = {
    val isStart = evaluateIsStart(event.newSignalState.value)
    if (isStart)
      cycleIdMap += (event.workCellId -> uuid)
    val cycleId = cycleIdMap(event.workCellId)
    val outgoingCycleEvent = OutgoingCycleEvent(cycleId, isStart, event.time, event.workCellId)
    val json = write(outgoingCycleEvent)
    println("From cycleChange: " + json)
    sendToBus(json)
  }

  def evaluateIsStart(value: Float): Boolean = {
    var result: Boolean = false
    if (value > 0)
      result = true
    result
  }

  def uuid: String = java.util.UUID.randomUUID.toString
}

object CycleChange {
  def props = Props[CycleChange]
}