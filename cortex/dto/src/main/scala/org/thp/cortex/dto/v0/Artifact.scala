package org.thp.cortex.dto.v0

import play.api.libs.json.{JsObject, Json, OFormat, OWrites}

case class InputArtifact(
    dataType: String,
    data: Option[String],
    tlp: Long,
    message: Option[String],
    parameters: JsObject
)

object InputArtifact {
  implicit val writes: OWrites[InputArtifact] = Json.writes[InputArtifact]
}

case class OutputArtifact(something: String) // TODO

object OutputArtifact {
  implicit val format: OFormat[OutputArtifact] = Json.format[OutputArtifact]
}
