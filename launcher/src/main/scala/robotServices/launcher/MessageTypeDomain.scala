package robotServices.launcher

import com.github.nscala_time.time.Imports._

/**
  * Created by Henrik on 2016-05-03.
  */

// For cycle events
case class CycleStartEvent(robotName: String,
                           cycleStart: DateTime)

case class CycleStopEvent(robotName: String,
                          cycleStop: DateTime)

// For the requestModules answer
case class ModulesReadEvent(robotName: String,
                            address: RobotDataAddress,
                            readValue: List[TaskWithModules])

case class Module(name: String,
                  isEncoded: Boolean,
                  isNoStepIn: Boolean,
                  isNoView: Boolean,
                  isReadOnly: Boolean,
                  isSystem: Boolean,
                  isViewOnly: Boolean,
                  programCode: List[String])

case class TaskWithModules(name: String,
                           `type`: Int,
                           cycle: Int,
                           executionType: Int,
                           executionStatus: Int,
                           modules: List[Module])

// For program pointer events
case class PointerChangedEvent(robotName: String,
                               robotDataAddress: RobotDataAddress,
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

// For tip dress data
case class TipDressEvent(robotName: String,
                         robotDataAddress: RobotDataAddress,
                         tipDressData: TipDressData)

case class TipDressData(tipDressWear: Float,
                        eventTime: DateTime)