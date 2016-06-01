package robotpath.service

import com.github.nscala_time.time.Imports._

/**
  * Created by Henrik on 2016-04-11.
  */

// For the requestModules answer
case class ModulesReadEvent(robotId: String,
                            workCellId: String,
                            address: Address,
                            readValue: List[TaskWithModules])

case class TaskWithModules(name: String,
                           modules: List[Module])

case class Module(name: String,
                  file: List[String])

// For program pointer events
case class FilledPointerChangedEvent(robotId: String,
                                     workCellId: String,
                                     address: Address,
                                     instruction: String,
                                     programPointerPosition: PointerPosition)

case class PointerChangedEvent(robotId: String,
                               workCellId: String,
                               address: Address,
                               programPointerPosition: PointerPosition)

case class Address(domain: String,
                   Kind: Int,
                   Path: List[String])

case class PointerPosition(task: String,
                           position: Position,
                           time: DateTime)

case class Position(module: String,
                    routine: String,
                    range: Range)

case class Range(begin: Location,
                 end: Location)

case class Location(column: Int,
                    row: Int)