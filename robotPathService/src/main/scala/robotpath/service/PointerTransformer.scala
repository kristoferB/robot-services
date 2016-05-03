package robotpath.service

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
  implicit val formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all // for json serialization

  // Type aliases
  type RobotId = String
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
  var robotMap: Map[RobotId, Map[TaskName, Map[ModuleName, Module]]] =
    Map[RobotId, Map[TaskName, Map[ModuleName, Module]]]()
  var taskMap: Map[TaskName, Map[ModuleName, Module]] = Map[TaskName, Map[ModuleName, Module]]()
  var moduleMap: Map[ModuleName, Module] = Map[ModuleName, Module]()

  // Functions
  def receive = {
    case "connect" =>
      ReActiveMQExtension(context.system).manager ! GetAuthenticatedConnection(s"nio://$address:61616", user, pass)
    case ConnectionEstablished(request, c) =>
      println("Connected: " + request)
      c ! ConsumeFromTopic(readFrom) // change to writeTo to be able to utilize the testMessageSender actor
      theBus = Some(c)
      requestModules()
    case ConnectionFailed(request, reason) =>
      println("Connection failed: " + reason)
    case mess @ AMQMessage(body, prop, headers) =>
      import Helpers.JValueExtended
      val json = parse(body.toString)
      if (json.has("readValue")) {
        val event: ModulesReadEvent = json.extract[ModulesReadEvent]
        event.readValue.foreach(task => {
          task.modules.foreach(module => moduleMap += (module.name -> module))
          taskMap += (task.name -> moduleMap)
          moduleMap = Map.empty[ModuleName, Module]
        })
        robotMap += (event.robotId -> taskMap)
        taskMap = Map.empty[TaskName, Map[ModuleName, Module]]
      } else if (json.has("programPointerPosition") & !json.has("instruction")) {
        val event: PointerChangedEvent = json.extract[PointerChangedEvent]
        fill(event)
      } else {
        // do nothing... OR println("Received message of unmanageable type property.")
      }
  }

  def fill(event: PointerChangedEvent) = {
    val eventPPPos = event.programPointerPosition
    if (robotMap.contains(event.robotId)) {
      if (robotMap(event.robotId).contains(eventPPPos.task.name)) {
        if (robotMap(event.robotId)(eventPPPos.task.name).contains(eventPPPos.position.moduleName)) {
          val module: Module = robotMap(event.robotId)(eventPPPos.task.name)(eventPPPos.position.moduleName)
          val range: Range = eventPPPos.position.range
          val instruction: Instruction = module.programCode(range.begin.row).
            slice(range.begin.column, range.end.column + 1)
          val filledEvent: FilledPointerChangedEvent =
            FilledPointerChangedEvent(event.robotId, event.robotDataAddress, instruction, eventPPPos)
          val json = write(filledEvent)
          sendToBus(json)
        } else
          println(s"The system ${event.robotId} does not contain the module called" +
            s"${eventPPPos.position.moduleName}")
      } else
        println(s"The system ${event.robotId} does not contain the task called" +
          s"${eventPPPos.task.name}")
    } else
      println(s"The system ${event.robotId} does not exist in the robot map.")
  }

  def requestModules() = {
    val json = write(Map[String, String]("Command" -> "ReadTasks", "Channel" -> "emulatedRobot",
      "DataPointId" -> "vcta.tasks", "path" -> "rapid.tasks"))
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