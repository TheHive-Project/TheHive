package org.thp.cortex.dto.client

import java.util.Date

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.json.{Json, OFormat}

case class InputCortexArtifact(
    tlp: Int,
    pap: Int,
    dataType: String,
    message: String,
    data: Option[String],
    attachment: Option[CortexAttachment]
)

object InputCortexArtifact {
  implicit val format: OFormat[InputCortexArtifact] = Json.format[InputCortexArtifact]
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
