package connectors.cortex.models

import play.api.libs.json.JsObject

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString

sealed abstract class CortexArtifact(attributes: JsObject)
case class FileArtifact(data: Source[ByteString, NotUsed], attributes: JsObject) extends CortexArtifact(attributes)
case class DataArtifact(data: String, attributes: JsObject)                      extends CortexArtifact(attributes)
