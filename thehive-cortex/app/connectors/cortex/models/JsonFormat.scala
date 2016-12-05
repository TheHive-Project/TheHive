package connectors.cortex.models

import java.io.File

import play.api.libs.json.{ JsObject, JsString, Json, OFormat, OWrites, Reads, Writes }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import org.elastic4play.models.JsonFormat.enumFormat
import play.api.libs.json.Format

object JsonFormat {
  val analyzerWrites = Writes[Analyzer](analyzer ⇒ Json.obj(
    "id" → analyzer.id,
    "name" → analyzer.name,
    "version" → analyzer.version,
    "description" → analyzer.description,
    "dataTypeList" → analyzer.dataTypeList,
    "cortexIds" → analyzer.cortexIds))
  val analyzerReads = Json.reads[Analyzer]
  implicit val analyzerFormats = Format(analyzerReads, analyzerWrites)

  val fileArtifactWrites = OWrites[FileArtifact](fileArtifact ⇒ Json.obj(
    "attributes" → fileArtifact.attributes))

  val fileArtifactReads = Reads[FileArtifact](json ⇒
    (json \ "attributes").validate[JsObject].map { attributes ⇒
      FileArtifact(new File("dummy"), attributes)
    })
  val fileArtifactFormat = OFormat(fileArtifactReads, fileArtifactWrites)
  val dataArtifactFormat = Json.format[DataArtifact]
  val artifactReads = Reads[CortexArtifact](json ⇒
    json.validateOpt[JsObject].flatMap {
      case a if a.contains("data") ⇒ json.validate[DataArtifact](dataArtifactFormat)
      case a                       ⇒ json.validate[FileArtifact](fileArtifactFormat)
    })
  val artifactWrites = OWrites[CortexArtifact](artifact ⇒ artifact match {
    case dataArtifact: DataArtifact ⇒ dataArtifactFormat.writes(dataArtifact)
    case fileArtifact: FileArtifact ⇒ fileArtifactWrites.writes(fileArtifact)
  })

  implicit val artifactFormat = OFormat(artifactReads, artifactWrites)
  implicit val jobStatusFormat = enumFormat(JobStatus)
  implicit val jobFormat = Json.format[CortexJob]
}