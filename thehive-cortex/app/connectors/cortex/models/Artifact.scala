package connectors.cortex.models

import play.api.libs.json.JsObject

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString

sealed abstract class CortexArtifact
case class FileArtifact(data: Source[ByteString, NotUsed], attributes: JsObject) extends CortexArtifact
case class DataArtifact(data: String, attributes: JsObject)                      extends CortexArtifact
