package org.thp.cortex.dto.v0

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class InputArtifact(
    tlp: Int,
    pap: Int,
    dataType: String,
    message: String,
    data: Option[String],
    attachment: Option[Attachment]
)

object InputArtifact {
  implicit val writes: Writes[InputArtifact] = (
    (JsPath \ "tlp").write[Int] and
      (JsPath \ "pap").write[Int] and
      (JsPath \ "dataType").write[String] and
      (JsPath \ "message").write[String] and
      (JsPath \ "data").writeNullable[String]
  )(i => (i.tlp, i.pap, i.dataType, i.message, i.data))
}
