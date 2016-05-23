package robotcyclestore.service

import com.github.nscala_time.time.Imports._

/**
  * Created by Henrik on 2016-04-15.
  */

// For cycle events
case class CycleStartEvent(workCellId: String,
                           cycleStart: DateTime)

case class CycleStopEvent(workCellId: String,
                          cycleStop: DateTime)

// For routine change events
case class RoutineChangedEvent(robotId: String,
                               workCellId: String,
                               activityId: String,
                               isStart: Boolean,
                               routineName: String,
                               eventTime: DateTime)

case class WorkCellCycle(workCellId: String,
                         entryId: String,
                         from: DateTime,
                         to: DateTime,
                         activities: Map[String, Map[String, List[Routine]]])

case class Routine(name: String,
                   from: DateTime,
                   to: DateTime)

// For program pointer events
case class PointerChangedEvent(robotId: String,
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