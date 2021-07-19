package org.thp.thehive.dto.v1

import org.thp.thehive.dto.{Description, String128, String16, String32}
import play.api.libs.json.{JsObject, Json, OFormat}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputTask(
    title: String128,
    group: Option[String32] = None,
    description: Option[Description] = None,
    status: Option[String16] = None,
    flag: Option[Boolean] = None,
    startDate: Option[Date] = None,
    endDate: Option[Date] = None,
    order: Option[Int] = None,
    dueDate: Option[Date] = None,
    assignee: Option[String128] = None
)

object InputTask {
  implicit val writes: OFormat[InputTask] = Json.format[InputTask]
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
    assignee: Option[String],
    order: Int,
    dueDate: Option[Date],
    extraData: JsObject
)

object OutputTask {
  implicit val format: OFormat[OutputTask] = Json.format[OutputTask]
}
