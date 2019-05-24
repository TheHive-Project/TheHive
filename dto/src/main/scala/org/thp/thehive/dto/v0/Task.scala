package org.thp.thehive.dto.v0

import java.util.Date

import play.api.libs.json.{Json, OFormat, OWrites}

case class InputTask(
    title: String,
    group: Option[String] = None,
    description: Option[String] = None,
    status: Option[String] = None,
    flag: Boolean = false,
    startDate: Option[Date] = None,
    endDate: Option[Date] = None,
    order: Option[Int] = None,
    dueDate: Option[Date] = None
)

object InputTask {
  implicit val writes: OWrites[InputTask] = Json.writes[InputTask]
}

case class OutputTask(
    id: String,
    _id: String,
    title: String,
    group: Option[String],
    description: Option[String],
    status: String,
    flag: Boolean,
    startDate: Option[Date],
    endDate: Option[Date],
    order: Int,
    dueDate: Option[Date]
)

object OutputTask {
  implicit val format: OFormat[OutputTask] = Json.format[OutputTask]
}
