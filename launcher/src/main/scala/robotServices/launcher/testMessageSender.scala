package robotServices.launcher

import akka.actor._
import com.codemettle.reactivemq.ReActiveMQMessages._
import com.codemettle.reactivemq._
import com.codemettle.reactivemq.model._
import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigFactory
import core.Domain._
import core.ServiceBase
import org.json4s.native.Serialization.write

/**
  * Created by Henrik on 2016-05-03.
  */

class testMessageSender extends ServiceBase {
  // Functions
  def receive = {
    case "connect" =>
      ReActiveMQExtension(context.system).manager ! GetAuthenticatedConnection(s"nio://$address:61616", user, pass)
    case ConnectionEstablished(request, c) =>
      println("connected: " + request)
      c ! ConsumeFromTopic(topic)
      theBus = Some(c)
      sendMessages()
    case ConnectionFailed(request, reason) =>
      println("failed: " + reason)
    case mess @ AMQMessage(body, prop, headers) => // does nothing
  }

  def sendMessages() = {

    val json1 = write(ModulesReadEvent("R1", "1197919", RapidAddress("rapid", "programPointer", List.empty),
      List[TaskWithModules](
      TaskWithModules("T_ROB1", List[Module](
        Module("module1", List[String]("Move1", "WaitUntil", "WaitDI", "Move2", "Move3", "WaitTime")),
        Module("module2", List[String]("Move12", "Stop12", "Move22", "Stop22", "Move32", "Stop32")))),
      TaskWithModules("T_ROB2", List[Module](
        Module("module1",List[String]("Move1", "Stop1", "Move2", "Stop2", "Move3", "Stop3")),
        Module("module2", List[String]("Move12", "Stop12", "Move22", "Stop22", "Move32", "Stop32"))))
      )))

    println("json1: " + json1)
    sendToBus(json1)

    val json2 = write(ModulesReadEvent("R2", "1197919", RapidAddress("rapid", "programPointer", List.empty),
      List[TaskWithModules](
        TaskWithModules("T_ROB1", List[Module](
          Module("module1", List[String]("Move1", "Stop1", "Move2", "Stop2", "Move3", "Stop3")))),
        TaskWithModules("T_ROB2", List[Module](
          Module("module1",List[String]("Move1", "Stop1", "Move2", "Stop2", "Move3", "Stop3"))))
      )))

    println("json2: " + json2)
    sendToBus(json2)

    Thread.sleep(2000)

    val json3: String = write(PointerChangedEvent("R1", "1197919", RapidAddress("rapid", "programPointer", List.empty),
      PointerPosition(Position("module1", "routine1", Range(Location(0,0), Location(5,0))), "T_ROB1", DateTime.now)))

    println("json3: " + json3)
    sendToBus(json3)

    Thread.sleep(1000)

    val json4: String = write(CycleEvent("cycle1", isStart = true, DateTime.now, "1197919"))
    println("json4: " + json4)
    sendToBus(json4)

    Thread.sleep(2000)

    val json5: String = write(PointerChangedEvent("R1", "1197919", RapidAddress("rapid", "programPointer", List.empty),
      PointerPosition(Position("module1", "routine1", Range(Location(0,1), Location(9,1))), "T_ROB1", DateTime.now)))

    println("json5: " + json5)
    sendToBus(json5)

    Thread.sleep(1000)

    /*val json6 = write(TipDressEvent("testId1", "1197919", RobotDataAddress("rapid", "data", ""),
      TipDressData(10,getNow)))
    println("json6: " + json6)
    sendToBus(json6)

    Thread.sleep(1000)*/

    val json8: String = write(PointerChangedEvent("R1", "1197919", RapidAddress("rapid", "programPointer", List.empty),
      PointerPosition(Position("module1", "routine1", Range(Location(0,2), Location(6,2))), "T_ROB1", DateTime.now)))

    println("json8: " + json8)
    sendToBus(json8)

    Thread.sleep(1000)

    /*val json9: String = write(TipDressEvent("testId1", "1197919", RobotDataAddress("rapid", "data", ""),
      TipDressData(8,getNow)))
    println("json9: " + json9)
    sendToBus(json9)

    Thread.sleep(3000)

    val json10: String = write(TipDressEvent("testId1", "1197919", RobotDataAddress("rapid", "data", ""),
      TipDressData(6,getNow)))
    println("json10: " + json10)
    sendToBus(json10)

    Thread.sleep(1000)*/

    val json11: String = write(PointerChangedEvent("R1", "1197919", RapidAddress("rapid", "programPointer", List.empty),
      PointerPosition(Position("module1", "routine2", Range(Location(0,3), Location(5,3))), "T_ROB1", DateTime.now)))

    println("json11: " + json11)
    sendToBus(json11)

    Thread.sleep(2000)

    val json12: String = write(CycleEvent("cycle1", isStart = false, DateTime.now, "1197919"))

    println("json12: " + json12)
    sendToBus(json12)

    /*val json14: String = write(TipDressEvent("testId1", "1197919", RobotDataAddress("rapid", "data", ""),
      TipDressData(4,getNow)))
    println("json14: " + json14)
    sendToBus(json14)

    Thread.sleep(1000)*/

    val json15: String = write(PointerChangedEvent("R1", "1197919", RapidAddress("rapid", "programPointer", List.empty),
      PointerPosition(Position("module1", "routine2", Range(Location(0,4), Location(5,4))), "T_ROB1", DateTime.now - 2.seconds)))

    println("json15: " + json15)
    sendToBus(json15)

    Thread.sleep(1000)

    val json16: String = write(PointerChangedEvent("R1", "1197919", RapidAddress("rapid", "programPointer", List.empty),
      PointerPosition(Position("module1", "routine3", Range(Location(0,5), Location(8,5))), "T_ROB1", DateTime.now - 3.seconds)))

    println("json16: " + json16)
    sendToBus(json16)

    Thread.sleep(1000)

    /*val json17: String = write(TipDressEvent("testId1", "1197919", RobotDataAddress("rapid", "data", ""),
      TipDressData(3,getNow)))
    println("json17: " + json17)
    sendToBus(json17)

    Thread.sleep(3000)

    val json18: String = write(TipDressEvent("testId1", "1197919", RobotDataAddress("rapid", "data", ""),
      TipDressData(2,getNow)))
    println("json18: " + json18)
    sendToBus(json18)

    Thread.sleep(3000)

    val json19: String = write(TipDressEvent("testId1", "1197919", RobotDataAddress("rapid", "data", ""),
      TipDressData(1,getNow)))
    println("json19: " + json19)
    sendToBus(json19)*/

    // THIS IS USED FOR TESTING THE CYCLE STORING SERVICE ONLY!
    /*val json1: String = write(ActivityEvent("routine1", isStart = true, "moveToBody", "R1", DateTime.now + 2.seconds, "routines", "1197919"))
    println("json1: " + json1)
    sendToBus(json1)

    Thread.sleep(1000)

    val json2: String = write(CycleEvent("cycle1", isStart = true, DateTime.now, "1197919"))
    println("json2: " + json2)
    sendToBus(json2)

    Thread.sleep(1000)

    val json3: String = write(ActivityEvent("routine1", isStart = false, "moveToBody", "R1", DateTime.now + 2.seconds, "routines", "1197919"))
    println("json3: " + json3)
    sendToBus(json3)

    val json4: String = write(ActivityEvent("routine2", isStart = true, "pickUpDoor", "R3", DateTime.now + 2.seconds, "routines", "1197919"))
    println("json4: " + json4)
    sendToBus(json4)

    Thread.sleep(1000)

    val json5: String = write(ActivityEvent("routine3", isStart = true, "goHome", "R2", DateTime.now, "routines", "1197919"))
    println("json5: " + json5)
    sendToBus(json5)

    Thread.sleep(2000)

    val json6: String = write(CycleEvent("cycle1", isStart = false, DateTime.now, "1197919"))
    println("json6: " + json6)
    sendToBus(json6)

    Thread.sleep(1000)

    val json7: String = write(ActivityEvent("routine3", isStart = false, "pickUpDoor", "R2", DateTime.now - 2.seconds, "routines", "1197919"))
    println("json7: " + json7)
    sendToBus(json7)

    val json8: String = write(ActivityEvent("routine2", isStart = false, "pickUpDoor", "R3", DateTime.now - 2.seconds, "routines", "1197919"))
    println("json8: " + json8)
    sendToBus(json8)

    Thread.sleep(10000)

    val json9: String = write(Map[String,RobotCycleSearchQuery]("robotCycleSearchQuery" -> RobotCycleSearchQuery(None, Some(TimeSpan(getNow - 18.days, getNow)), "1197919")))
    println("json9: " + json9)
    sendToBus(json9)*/
  }

}

object testMessageSender {
  def props = Props[testMessageSender]
}