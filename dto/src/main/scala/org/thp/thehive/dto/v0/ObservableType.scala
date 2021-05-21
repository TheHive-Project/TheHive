package org.thp.thehive.dto.v0

import org.thp.thehive.dto.String64
import play.api.libs.json.{Json, OFormat, Writes}
import be.venneborg.refined.play.RefinedJsonFormats._

import java.util.Date

case class InputObservableType(
    name: String64,
    isAttachment: Option[Boolean]
)
object InputObservableType {
  implicit val writes: Writes[InputObservableType] = Json.writes[InputObservableType]
}

case class OutputObservableType(
    name: String,
    isAttachment: Boolean,
    _id: String,
    id: String,
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    _type: String
)

object OutputObservableType {
  implicit val format: OFormat[OutputObservableType] = Json.format[OutputObservableType]
}
