package org.thp.thehive.dto.v0

import org.thp.thehive.dto.{Description, String64}
import play.api.libs.json.{Format, Json, Writes}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputOrganisation(
    name: String64,
    description: Description,
    taskRule: Option[String64],
    observableRule: Option[String64]
)

object InputOrganisation {
  implicit val writes: Writes[InputOrganisation] = Json.writes[InputOrganisation]
}

case class OutputOrganisation(
    name: String,
    description: String,
    taskRule: String,
    observableRule: String,
    _id: String,
    id: String,
    createdAt: Date,
    createdBy: String,
    updatedAt: Option[Date],
    updatedBy: Option[String],
    _type: String,
    links: Seq[String]
)

object OutputOrganisation {
  implicit val format: Format[OutputOrganisation] = Json.format[OutputOrganisation]
}
