package org.thp.thehive.dto.v1

import java.util.Date

import play.api.libs.json.{Json, OFormat}

import org.thp.scalligraph.controllers.FFile

case class InputLog(message: String, startDate: Option[Date] = None, attachment: Option[FFile] = None)

case class OutputLog(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    message: String,
    startDate: Date,
    attachment: Option[OutputAttachment] = None,
    status: String,
    owner: String
)

object OutputLog {
  implicit val format: OFormat[OutputLog] = Json.format[OutputLog]
}
