package org.thp.thehive.dto.v1

import java.util.Date

import play.api.libs.json.{Format, Json, Writes}

case class InputOrganisation(name: String, description: String)

object InputOrganisation {
  implicit val writes: Writes[InputOrganisation] = Json.writes[InputOrganisation]
}

case class OutputOrganisation(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    name: String,
    description: String
)

object OutputOrganisation {
  implicit val format: Format[OutputOrganisation] = Json.format[OutputOrganisation]
}
