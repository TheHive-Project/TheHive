package org.thp.thehive.dto.v1

import org.thp.thehive.dto.String64
import org.thp.thehive.dto.v1.ObservablesFilter.ObservablesFilter
import org.thp.thehive.dto.v1.TasksFilter.TasksFilter
import play.api.libs.json.{Format, Json, Writes}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputShare(organisationName: String64, profile: String64, tasks: TasksFilter, observables: ObservablesFilter)

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
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    caseId: String,
    profileName: String,
    organisationName: String,
    owner: Boolean
)

object OutputShare {
  implicit val format: Format[OutputShare] = Json.format[OutputShare]
}
