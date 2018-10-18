package org.thp.thehive.dto.v1

import java.util.Date

import play.api.libs.json.{Format, Json, Writes}

case class InputTask(
    caseId: String,
    title: String,
    description: Option[String] = None,
    status: Option[String] = None,
    flag: Boolean = false,
    startDate: Option[Date] = None,
    endDate: Option[Date] = None,
    order: Option[Int] = None,
    dueDate: Option[Date] = None)

object InputTask {
  implicit val writes: Writes[InputTask] = Json.writes[InputTask]
}

case class OutputTask(
    title: String,
    description: Option[String],
    status: String,
    flag: Boolean,
    startDate: Option[Date],
    endDate: Option[Date],
    order: Int,
    dueDate: Option[Date])

object OutputTask {
  implicit val format: Format[OutputTask] = Json.format[OutputTask]
}
