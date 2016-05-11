package robotroutinechange.service

import com.github.nscala_time.time.Imports._

/**
  * Created by Henrik on 2016-05-10.
  */

// For routine change events
case class RoutineChangedEvent(robotName: String,
                               workCellName: String,
                               startFlag: Boolean,
                               routineName: String,
                               eventTime: DateTime)

// For program pointer events
case class PointerChangedEvent(robotName: String,
                               workCellName: String,
                               robotDataAddress: RobotDataAddress,
                               instruction: String,
                               isWaiting: Boolean,
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