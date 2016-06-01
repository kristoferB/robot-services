package core

import com.github.nscala_time.time.Imports._

/**
  * Created by daniel on 2016-06-01.
  */
object Domain {
  // Tip Dressing
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

  // Activities
  case class Activity(id: String,
                      from: DateTime,
                      name: String,
                      to: DateTime,
                      `type`: String)

  case class ActivityEvent(activityId: String,
                           isStart: Boolean,
                           name: String,
                           robotId: String,
                           time: DateTime,
                           `type`: String,
                           workCellId: String)

  case class CycleEvent(cycleId: String,
                        isStart: Boolean,
                        time: DateTime,
                        workCellId: String)

  // Cycle Fold, Store and Search
  case class WorkCellCycle(workCellId: String,
                           id: String,
                           from: DateTime,
                           to: DateTime,
                           activities: Map[String, Map[String, List[Activity]]])

  case class RobotCycleSearchQuery(cycleId: Option[String],
                                   timeSpan: Option[TimeSpan],
                                   workCellId: String)

  case class TimeSpan(start: DateTime,
                      stop: DateTime)

  case class RobotCyclesResponse(workCellId: String,
                                 foundCycles: List[WorkCellCycle])

  // Robot Endpoint
  case class RapidAddress(domain: String,
                          kind: String,
                          path: List[String])


  // Program Pointer
  case class PointerChangedEvent(robotId: String,
                                 workCellId: String,
                                 address: RapidAddress,
                                 instruction: String,
                                 isWaiting: Boolean,
                                 programPointerPosition: PointerPosition)

  case class PointerPosition(eventTime: DateTime,
                             module: Module,
                             position: Position,
                             task: String)

  case class Position(moduleName: String,
                      routineName: String,
                      range: Range)

  case class Range(begin: Location,
                   end: Location)

  case class Location(column: Int,
                      row: Int)

  // RAPID Modules
  case class ModulesReadEvent(robotId: String,
                              workCellId: String,
                              address: RapidAddress,
                              readValue: List[TaskWithModules])

  case class TaskWithModules(name: String,
                             `type`: Int,
                             cycle: Int,
                             executionType: Int,
                             executionStatus: Int,
                             modules: List[Module])

  case class Module(name: String,
                    programCode: List[String])


  // Instruction Fill
  case class PointerWithInstruction(robotId: String,
                                    workCellId: String,
                                    address: RapidAddress,
                                    instruction: String,
                                    programPointerPosition: PointerPosition)

  // Is Waiting Fill
  case class PointerWithIsWaiting(robotId: String,
                                  workCellId: String,
                                  address: RapidAddress,
                                  instruction: String,
                                  isWaiting: Boolean,
                                  programPointerPosition: PointerPosition)
}