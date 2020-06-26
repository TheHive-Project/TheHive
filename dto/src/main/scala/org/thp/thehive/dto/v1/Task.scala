package org.thp.thehive.dto.v1

import java.util.Date

import play.api.libs.json.{Json, OFormat, OWrites}

case class InputTask(
    title: String,
    group: Option[String] = None,
    description: Option[String] = None,
    status: Option[String] = None,
    flag: Option[Boolean] = None,
    startDate: Option[Date] = None,
    endDate: Option[Date] = None,
    order: Option[Int] = None,
    dueDate: Option[Date] = None
)

object InputTask {
  implicit val writes: OWrites[InputTask] = Json.writes[InputTask]
}

case class OutputTask(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    title: String,
    group: String,
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
