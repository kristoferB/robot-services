package robotiswaiting.service

import com.github.nscala_time.time.Imports._

/**
  * Created by Henrik on 2016-04-11.
  */

// For program pointer events
case class FilledPointerChangedEvent(robotId: String,
                                     robotDataAddress: RobotDataAddress,
                                     instruction: String,
                                     isWaiting: Boolean,
                                     programPointerPosition: PointerPosition)

case class PointerChangedEvent(robotId: String,
                               robotDataAddress: RobotDataAddress,
                               instruction: String,
                               programPointerPosition: PointerPosition)

case class RobotDataAddress(domain: String,
                            kind: String,
                            path: String)

case class PointerPosition(task: Task,
                           position: Position,
                           eventTime: DateTime)

case class Task(name: String,
                `type`: Int,
                cycle: Int,
                executionType: Int,
                executionStatus: Int)

case class Position(moduleName: String,
                    routineName: String,
                    range: Range)

case class Range(begin: Location,
                 end: Location)

case class Location(column: Int,
                    row: Int)