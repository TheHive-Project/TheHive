package org.thp.thehive.dto.v0

import java.util.Date

import org.thp.thehive.dto.v0.ObservablesFilter.ObservablesFilter
import org.thp.thehive.dto.v0.TasksFilter.TasksFilter
import play.api.libs.json.{Format, Json, Writes}

case class InputShare(organisationName: String, profile: String, tasks: TasksFilter, observables: ObservablesFilter)

object TasksFilter extends Enumeration {
  type TasksFilter = Value

  val all: TasksFilter  = Value("all")
  val none: TasksFilter = Value("none")

  implicit val format: Format[TasksFilter] = Json.formatEnum(TasksFilter)
}

object ObservablesFilter extends Enumeration {
  type ObservablesFilter = Value

  val all: ObservablesFilter  = Value("all")
  val none: ObservablesFilter = Value("none")

  implicit val format: Format[ObservablesFilter] = Json.formatEnum(ObservablesFilter)
}

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
