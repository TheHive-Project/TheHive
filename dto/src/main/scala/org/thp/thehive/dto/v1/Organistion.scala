package org.thp.thehive.dto.v1
import play.api.libs.json.{Format, Json, Writes}

case class InputOrganisation(name: String, description: String)

object InputOrganisation {
  implicit val writes: Writes[InputOrganisation] = Json.writes[InputOrganisation]
}

case class OutputOrganisation(name: String, description: String)

object OutputOrganisation {
  implicit val format: Format[OutputOrganisation] = Json.format[OutputOrganisation]
}
