package robotpath.service

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

class PointerTransformer extends Actor {
  val customDateFormat = new DefaultFormats {
    override def dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
  }
  implicit val formats = customDateFormat ++ org.json4s.ext.JodaTimeSerializers.all // for json serialization

  // Type aliases
  type RobotName = String
  type TaskName = String
  type ModuleName = String
  type Instruction = String

  // Read from config file
  val config = ConfigFactory.load()
  val address = config.getString("activemq.address")
  val user = config.getString("activemq.user")
  val pass = config.getString("activemq.pass")
  val readFrom = config.getString("service.robotPath.readFromTopic")
  val writeTo = config.getString("service.robotPath.writeToTopic")

  // The state
  var theBus: Option[ActorRef] = None

  // Local variables
  var robotMap: Map[RobotName, Map[TaskName, Map[ModuleName, Module]]] =
    Map[RobotName, Map[TaskName, Map[ModuleName, Module]]]()
  var taskMap: Map[TaskName, Map[ModuleName, Module]] = Map[TaskName, Map[ModuleName, Module]]()
  var moduleMap: Map[ModuleName, Module] = Map[ModuleName, Module]()

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
      println(json)
      if (json.has("readResult")) {
        println("Recievied result...")
        val event: ModulesReadEvent = json.extract[ModulesReadEvent]
        event.readValue.foreach(task => {
          task.modules.foreach(module => moduleMap += (module.name -> module))
          taskMap += (task.name -> moduleMap)
          moduleMap = Map.empty[ModuleName, Module]
        })
        robotMap += (event.robotId -> taskMap)
        taskMap = Map.empty[TaskName, Map[ModuleName, Module]]
        println(robotMap)
      } else if (json.has("programPointerPosition") && !json.has("instruction")) {
        println(getNow)
        val event: PointerChangedEvent = json.extract[PointerChangedEvent]
        fill(event)
      } else {
        // do nothing... OR println("Received message of unmanageable type property.")
      }
  }

  def fill(event: PointerChangedEvent) = {
    val eventPPPos = event.programPointerPosition
    if (robotMap.contains(event.robotId)) {
      if (robotMap(event.robotId).contains(eventPPPos.task)) {
        if (robotMap(event.robotId)(eventPPPos.task).contains(eventPPPos.position.module)) {
          val module: Module = robotMap(event.robotId)(eventPPPos.task)(eventPPPos.position.module)
          val range: Range = eventPPPos.position.range
          val instruction: Instruction = module.programCode(range.begin.row).
            slice(range.begin.column, range.end.column + 1)
          val filledEvent: FilledPointerChangedEvent =
            FilledPointerChangedEvent(event.robotId, event.workCellId, event.address, instruction, eventPPPos)
          val json = write(filledEvent)
          println("From instruction filler: " + json)
          sendToBus(json)
        } else
          println(s"The system ${event.robotId} does not contain the module called" +
            s"${eventPPPos.position.module}")
      } else
        println(s"The system ${event.robotId} does not contain the task called" +
          s"${eventPPPos.task}")
    } else
      requestModules(event)
  }

  def requestModules(event: PointerChangedEvent) = {
    import org.json4s.JsonDSL._
    val jAddress = ("domain" -> "rapid") ~ ("kind" -> "tasks")
    val json = ("command" -> "read") ~ ("robotId" -> event.robotId) ~ ("address" -> jAddress)
    println(s"Requesting modules for robot id ${event.robotId}." + json)
    sendToBus(write(json))
  }

  def sendToBus(json: String) = {
    theBus.foreach{bus => bus ! SendMessage(Topic(writeTo), AMQMessage(json))}
  }

  override def postStop() = {
    theBus.foreach(_ ! CloseConnection)
  }

  def getNow = {
    DateTime.now//DateTimeZone.forID("Europe/Stockholm")
  }
}

object PointerTransformer {
  def props = Props[PointerTransformer]
}