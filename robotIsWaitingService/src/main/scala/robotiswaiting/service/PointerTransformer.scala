package robotiswaiting.service

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

class PointerTransformer extends Actor {
  val customDateFormat = new DefaultFormats {
    override def dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
  }
  implicit val formats = customDateFormat ++ org.json4s.ext.JodaTimeSerializers.all // for json serialization

  // Type aliases
  type Instruction = String

  // Read from config file
  val config = ConfigFactory.load()
  val address = config.getString("activemq.address")
  val user = config.getString("activemq.user")
  val pass = config.getString("activemq.pass")
  val readFrom = config.getString("service.robotIsWaiting.readFromTopic")
  val writeTo = config.getString("service.robotIsWaiting.writeToTopic")

  // The state
  var theBus: Option[ActorRef] = None

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
    val filledEvent: FilledPointerChangedEvent = FilledPointerChangedEvent(event.robotId, event.workCellId,
      event.address, instruction, isWaiting, event.programPointerPosition)
    val json: String = write(filledEvent)
    println("From isWaiting: " + json)
    sendToBus(json)
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

object PointerTransformer {
  def props = Props[PointerTransformer]
}