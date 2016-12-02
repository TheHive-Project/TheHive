package connectors.cortex.models

import java.io.File

import play.api.libs.json.JsObject

sealed abstract class CortexArtifact(attributes: JsObject)
case class FileArtifact(data: File, attributes: JsObject) extends CortexArtifact(attributes)
case class DataArtifact(data: String, attributes: JsObject) extends CortexArtifact(attributes)