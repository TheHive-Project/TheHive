package org.thp.thehive.dto.v0

import org.thp.thehive.dto.{Description, String128, String512}
import play.api.libs.json._

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputPage(
    title: String512,
    content: Description,
    order: Option[Int],
    category: String128
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
    _type: String,
    slug: String,
    order: Int,
    category: String
)

object OutputPage {
  implicit val format: OFormat[OutputPage] = Json.format[OutputPage]
}
