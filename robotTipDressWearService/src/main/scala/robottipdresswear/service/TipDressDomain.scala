package robottipdresswear.service

import com.github.nscala_time.time.Imports._

/**
  * Created by Henrik on 2016-04-11.
  */

// For tip dress events
case class TipDressWarningEvent(robotId: String,
                                workCellId: String,
                                robotDataAddress: RobotDataAddress,
                                cutterWarning: Boolean)

case class TipDressEvent(robotId: String,
                         workCellId: String,
                         robotDataAddress: RobotDataAddress,
                         tipDressData: TipDressData)

case class RobotDataAddress(domain: String,
                            kind: String,
                            path: String)

case class TipDressData(tipDressWear: Float,
                        eventTime: DateTime)