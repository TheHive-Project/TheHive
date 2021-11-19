package org.thp.cortex.dto.v0

import play.api.libs.json._

case class InputArtifact(
    tlp: Int,
    pap: Int,
    dataType: String,
    message: String,
    data: Option[String],
    attachment: Option[Attachment],
    parameters: JsObject
)

object InputArtifact {
  implicit val writes: Writes[InputArtifact] = Writes[InputArtifact] { a =>
    Json.obj(
      "tlp"        -> a.tlp,
      "pap"        -> a.pap,
      "dataType"   -> a.dataType,
      "message"    -> a.message,
      "data"       -> a.data,
      "parameters" -> a.parameters
    )
  }
}
