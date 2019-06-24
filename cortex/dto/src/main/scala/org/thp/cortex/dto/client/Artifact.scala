package org.thp.cortex.dto.client

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.json.{Json, OFormat}

case class InputCortexArtifact(
    tlp: Int,
    pap: Int,
    dataType: String,
    message: String,
    data: Option[String],
    attachment: Option[String] // TODO
)

object InputCortexArtifact {
  implicit val format: OFormat[InputCortexArtifact] = Json.format[InputCortexArtifact]
}

//case class OutputCortexArtifact(
//    )
//
//object OutputCortexArtifact {
//  implicit val format: OFormat[OutputCortexArtifact] = Json.format[OutputCortexArtifact]
//}
