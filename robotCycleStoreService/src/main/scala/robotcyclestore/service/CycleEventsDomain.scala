package robotcyclestore.service

import com.github.nscala_time.time.Imports._

/**
  * Created by Henrik on 2016-04-15.
  */

case class CycleEvent(cycleId: String,
                      isStart: Boolean,
                      time: DateTime,
                      workCellId: String)

case class ActivityEvent(activityId: String,
                         isStart: Boolean,
                         name: String,
                         robotId: String,
                         time: DateTime,
                         `type`: String,
                         workCellId: String)

case class Activity(id: String,
                    from: DateTime,
                    name: String,
                    to: DateTime,
                    `type`: String)

case class WorkCellCycle(workCellId: String,
                         id: String,
                         from: DateTime,
                         to: DateTime,
                         activities: Map[String, Map[String, List[Activity]]])

// For drawing service events
case class RobotCycleSearchQuery(cycleId: Option[String],
                                 timeSpan: Option[TimeSpan],
                                 workCellId: String)

case class TimeSpan(start: DateTime,
                    stop: DateTime)

case class RobotCyclesResponse(workCellId: String,
                               foundCycles: List[WorkCellCycle])

// For program pointer events
case class PointerChangedEvent(robotId: String,
                               address: Address,
                               instruction: String,
                               isWaiting: Boolean,
                               programPointerPosition: PointerPosition)

case class Address(domain: String,
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