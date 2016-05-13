package robotpath.service

import com.github.nscala_time.time.Imports._

/**
  * Created by Henrik on 2016-04-11.
  */

// For the requestModules answer
case class ModulesReadEvent(robotId: String,
                            workCellId: String,
                            robotDataAddress: RobotDataAddress,
                            readValue: List[TaskWithModules])

case class TaskWithModules(name: String,
                           `type`: Int,
                           cycle: Int,
                           executionType: Int,
                           executionStatus: Int,
                           modules: List[Module])

case class Module(name: String,
                  isEncoded: Boolean,
                  isNoStepIn: Boolean,
                  isNoView: Boolean,
                  isReadOnly: Boolean,
                  isSystem: Boolean,
                  isViewOnly: Boolean,
                  programCode: List[String])

// For program pointer events
case class FilledPointerChangedEvent(robotId: String,
                                     workCellId: String,
                                     robotDataAddress: RobotDataAddress,
                                     instruction: String,
                                     programPointerPosition: PointerPosition)

case class PointerChangedEvent(robotId: String,
                               workCellId: String,
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