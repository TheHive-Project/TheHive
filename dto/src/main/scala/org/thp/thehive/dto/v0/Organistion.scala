package org.thp.thehive.dto.v0

import play.api.libs.json.{Format, Json, Writes}

case class InputOrganisation(name: String)

object InputOrganisation {
  implicit val writes: Writes[InputOrganisation] = Json.writes[InputOrganisation]
}

case class OutputOrganisation(name: String)

object OutputOrganisation {
  implicit val format: Format[OutputOrganisation] = Json.format[OutputOrganisation]
}
