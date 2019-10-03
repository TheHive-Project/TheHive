package org.thp.cortex.dto.v0

import java.util.Date

import play.api.libs.functional.syntax._
import play.api.libs.json._

import akka.stream.scaladsl.Source
import akka.util.ByteString

case class InputCortexArtifact(
    tlp: Int,
    pap: Int,
    dataType: String,
    message: String,
    data: Option[String],
    attachment: Option[Attachment]
)

object InputCortexArtifact {
  implicit val writes: Writes[InputCortexArtifact] = (
    (JsPath \ "tlp").write[Int] and
      (JsPath \ "pap").write[Int] and
      (JsPath \ "dataType").write[String] and
      (JsPath \ "message").write[String] and
      (JsPath \ "data").writeNullable[String]
  )(i => (i.tlp, i.pap, i.dataType, i.message, i.data))
}

case class OutputCortexArtifact(
    id: String,
    workerId: String,
    workerName: String,
    workerDefinition: String,
    date: Date,
    status: String
)

object OutputCortexArtifact {
  implicit val format: OFormat[OutputCortexArtifact] = Json.format[OutputCortexArtifact]
}

case class CortexAttachment(name: String, size: Long, contentType: String, data: Source[ByteString, _])
