package org.thp.thehive.dto.v1

import java.util.Date

import org.thp.scalligraph.controllers.FFile
import play.api.libs.json.{JsObject, Json, OFormat}

case class InputLog(message: String, startDate: Option[Date] = None, attachment: Option[FFile] = None)

case class OutputLog(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    message: String,
    date: Date,
    attachment: Option[OutputAttachment] = None,
    owner: String,
    extraData: JsObject
)

object OutputLog {
  implicit val format: OFormat[OutputLog] = Json.format[OutputLog]
}
