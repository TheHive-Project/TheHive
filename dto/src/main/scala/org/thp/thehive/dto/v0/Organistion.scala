package org.thp.thehive.dto.v0

import java.util.Date

import play.api.libs.json.{Format, Json, Writes}

case class InputOrganisation(name: String, description: String)

object InputOrganisation {
  implicit val writes: Writes[InputOrganisation] = Json.writes[InputOrganisation]
}

case class OutputOrganisation(
    name: String,
    description: String,
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
