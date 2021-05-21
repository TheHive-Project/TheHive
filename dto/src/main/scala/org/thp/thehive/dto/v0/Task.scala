package org.thp.thehive.dto.v0

import org.thp.thehive.dto.{Description, String128, String16, String32}
import play.api.libs.json.{Json, OFormat, OWrites}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputTask(
    title: String128,
    group: Option[String32] = None,
    description: Option[Description] = None,
    owner: Option[String128] = None,
    status: Option[String16] = None,
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
    id: String,
    _id: String,
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    _type: String,
    title: String,
    group: String,
    description: Option[String],
    owner: Option[String],
    status: String,
    flag: Boolean,
    startDate: Option[Date],
    endDate: Option[Date],
    order: Int,
    dueDate: Option[Date],
    `case`: Option[OutputCase]
)

object OutputTask {
  implicit val format: OFormat[OutputTask] = Json.format[OutputTask]
}
