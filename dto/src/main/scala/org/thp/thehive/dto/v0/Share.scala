package org.thp.thehive.dto.v0

import org.thp.thehive.dto.String64
import play.api.libs.json.{Format, Json, Writes}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

//case class InputShare2(
//    organisationName: String64,
//    profile: String64,
//    tasks: String64,
//    observables: String64
//)

//object TasksFilter extends Enumeration {
//  type TasksFilter = Value
//
//  val all: TasksFilter  = Value("all")
//  val none: TasksFilter = Value("none")
//
//  implicit val format: Format[TasksFilter] = Json.formatEnum(TasksFilter)
//}
//
//object ObservablesFilter extends Enumeration {
//  type ObservablesFilter = Value
//
//  val all: ObservablesFilter  = Value("all")
//  val none: ObservablesFilter = Value("none")
//
//  implicit val format: Format[ObservablesFilter] = Json.formatEnum(ObservablesFilter)
//}

case class InputShare(
    organisation: String64,
    share: Option[Boolean],
    profile: Option[String64],
    taskRule: Option[String64],
    observableRule: Option[String64]
)

object InputShare {
  implicit val writes: Writes[InputShare] = Json.writes[InputShare]
}

case class OutputShare(
    _id: String,
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    caseId: String,
    profileName: String,
    organisationName: String,
    owner: Boolean
)

object OutputShare {
  implicit val format: Format[OutputShare] = Json.format[OutputShare]
}
