package robotcyclestore.service

import org.json4s._

/**
  * Created by Henrik on 2016-04-20.
  */

object Helpers {
  implicit class JValueExtended(value: JValue) {
    def has(childString: String): Boolean = {
      if ((value \ childString) != JNothing)
        true
      else
        false
    }
  }
}