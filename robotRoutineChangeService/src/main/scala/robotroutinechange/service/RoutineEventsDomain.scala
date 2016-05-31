package robotroutinechange.service

import com.github.nscala_time.time.Imports._

/**
  * Created by Henrik on 2016-05-10.
  */

// For routine change events
case class RoutineChangedEvent(robotId: String,
                               workCellId: String,
                               activityId: String,
                               isStart: Boolean,
                               routineName: String,
                               eventTime: DateTime)

// For program pointer events
case class PointerChangedEvent(robotId: String,
                               workCellId: String,
                               address: Address,
                               instruction: String,
                               isWaiting: Boolean,
                               programPointerPosition: PointerPosition)

case class Address(domain: String,
                   Kind: Int,
                   Path: List[String])

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