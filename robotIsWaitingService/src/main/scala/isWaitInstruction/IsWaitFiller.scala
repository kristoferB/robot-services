package isWaitInstruction

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
  * Created by Henrik on 2016-04-08.
  */

/*
Reads the instruction of a pointer event which comes from robot-path-service and extends it with a boolean value
indicating whether the robot issues a wait* RAPID instruction of not.
*Available RAPID wait instructions:
- WaitAI: Waits until an analog input signal value is set.
- WaitAO: Waits until an analog output signal value is set.
- WaitDI: Waits until a digital input signal value is set.
- WaitDO: Waits until a digital output signal value is set.
- WaitGI: Waits until a group of digital input signals are set.
- WaitGO: Waits until a group of digital output signals are set.
- WaitLoad: Connect the loaded module to the task.
- WaitRob: Wait until stop point or zero speed.
- WaitSyncTask: Wait at synchronization point for other program tasks.
- WaitTestAndSet: Wait until variable unset - then set.
- WaitTime: Waits a given amount of time.
- WaitUntil: Waits until a condition is met.
- WaitWObj: Wait for work object on conveyor.
*/

class IsWaitFiller extends ServiceBase {
  // Type aliases
  type Instruction = String

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
      if (json.has("programPointerPosition") && json.has("instruction") && !json.has("isWaiting")) {
        val event: PointerChangedEvent = json.extract[PointerChangedEvent]
        fill(event)
      } else {
        // do nothing... OR println("Received message of unmanageable type property.")
      }
  }

  def fill(event: PointerChangedEvent) = {
    val instruction: Instruction = event.instruction
    var isWaiting: Boolean = false
    if (instruction.startsWith("Wait")) {
      isWaiting = true
    }
    val filledEvent = PointerWithIsWaiting(event.robotId, event.workCellId,
      event.address, instruction, isWaiting, event.programPointerPosition)
    val json: String = write(filledEvent)
    println("From isWaiting: " + json)
    sendToBus(json)
  }

}

object IsWaitFiller {
  def props = Props[IsWaitFiller]
}