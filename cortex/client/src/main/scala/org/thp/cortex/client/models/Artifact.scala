package org.thp.cortex.client.models

import play.api.libs.json.{Json, OFormat}

case class Artifact(
    tlp: Int,
    pap: Int,
    dataType: String,
    message: String,
    data: Option[String],
    attachment: Option[Attachment]
)

object Artifact {
  implicit val format: OFormat[Artifact] = Json.format[Artifact]
}

//case class OutputCortexArtifact(
//    )
//
//object OutputCortexArtifact {
//  implicit val format: OFormat[OutputCortexArtifact] = Json.format[OutputCortexArtifact]
//}
