package org.thp.thehive.dto.v1

import play.api.libs.json.{Json, OFormat, Writes}

import java.util.Date

case class InputObservableType(name: String, isAttachment: Option[Boolean])

object InputObservableType {
  implicit val writes: Writes[InputObservableType] = Json.writes[InputObservableType]
}

case class OutputObservableType(
    _id: String,
    _type: String,
    _updatedAt: Option[Date] = None,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _createdBy: String,
    name: String,
    isAttachment: Boolean
)

object OutputObservableType {
  implicit val format: OFormat[OutputObservableType] = Json.format[OutputObservableType]
}
