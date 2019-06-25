package org.thp.cortex.client.models

import java.util.Date

import akka.stream.scaladsl.Source
import akka.util.ByteString
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
