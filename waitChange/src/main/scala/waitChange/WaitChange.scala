package waitChange

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
  * Created by Daniel on 2016-06-02.
  */

/*
Emits activity events when a robot toggles wait.
*/

class WaitChange extends ServiceBase {
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
      if (json.has("isWaiting")) {
        val event: PointerWithIsWaiting = json.extract[PointerWithIsWaiting]
        fill(event)
      } else {
        // do nothing... OR println("Received message of unmanageable type property.")
      }
  }

  def fill(event: PointerWithIsWaiting) = {


  }

}

object WaitChange {
  def props = Props[WaitChange]
}