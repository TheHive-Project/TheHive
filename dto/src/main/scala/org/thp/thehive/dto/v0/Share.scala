package org.thp.thehive.dto.v0

import java.util.Date

import play.api.libs.json.{Format, Json, Writes}

case class InputShare(caseId: String, organisationName: String, profile: String, tasks: String, observables: String)

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

  val tasksFilterParser = ???

  val observablesFilterParser = ???
}

case class OutputShare(
    _id: String,
    id: String,
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    _type: String,
    caseId: String,
    organisationName: String,
    profile: String
)

object OutputShare {
  implicit val format: Format[OutputShare] = Json.format[OutputShare]
}
