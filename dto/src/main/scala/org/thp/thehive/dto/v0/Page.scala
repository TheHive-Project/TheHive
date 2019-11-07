package org.thp.thehive.dto.v0

import java.util.Date

import play.api.libs.json._

case class InputPage(
    title: String,
    content: String
)

object InputPage {
  implicit val writes: OWrites[InputPage] = Json.writes[InputPage]
}

case class OutputPage(
    _id: String,
    id: String,
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    title: String,
    content: String,
    _type: String
)

object OutputPage {
  implicit val format: OFormat[OutputPage] = Json.format[OutputPage]
}
