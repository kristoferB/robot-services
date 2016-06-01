package core

import java.text.SimpleDateFormat

import akka.actor.{Actor, ActorRef}
import com.codemettle.reactivemq.ReActiveMQMessages.{CloseConnection, SendMessage}
import com.codemettle.reactivemq.model.{AMQMessage, Topic}
import com.typesafe.config.ConfigFactory
import org.json4s.DefaultFormats
import wabisabi.Client

trait ServiceBase extends Actor {
  val customDateFormat = new DefaultFormats {
    override def dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
  }
  implicit val formats = customDateFormat ++ org.json4s.ext.JodaTimeSerializers.all // for json serialization

  // Read from config file
  val config = ConfigFactory.load()
  val address = config.getString("activemq.address")
  val user = config.getString("activemq.user")
  val pass = config.getString("activemq.pass")
  val topic = config.getString("activemq.topic")
  val elasticIP = config.getString("elastic.ip")
  val elasticPort = config.getString("elastic.port")

  // The state
  var theBus: Option[ActorRef] = None

  def sendToBus(json: String) = {
    theBus.foreach{bus => bus ! SendMessage(Topic(topic), AMQMessage(json))}
  }

  override def postStop() = {
    theBus.foreach(_ ! CloseConnection)
    Client.shutdown()
  }
}
