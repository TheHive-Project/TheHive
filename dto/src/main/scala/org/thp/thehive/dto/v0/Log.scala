package org.thp.thehive.dto.v0
import java.util.Date

import play.api.libs.json.{Json, OFormat}

import org.thp.scalligraph.controllers.FFile

case class InputLog(message: String, startDate: Option[Date] = None, attachment: Option[FFile] = None)

case class OutputLog(
    _id: String,
    id: String, // _id
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    _type: String,
    message: String,
    startDate: Date,
    attachment: Option[OutputAttachment] = None,
    status: String,
    owner: String
)

object OutputLog {
  implicit val format: OFormat[OutputLog] = Json.format[OutputLog]
}
