package connectors.cortex.models

import akka.stream.scaladsl.Source

import play.api.libs.json.{ Format, JsObject, Json }
import play.api.libs.json.{ OFormat, OWrites, Reads, Writes }
import play.api.libs.json.Json.toJsFieldJsValueWrapper

import org.elastic4play.models.JsonFormat.enumFormat
import java.util.Date

object JsonFormat {
  val analyzerWrites = Writes[Analyzer](analyzer ⇒ Json.obj(
    "id" → analyzer.id,
    "name" → analyzer.name,
    "version" → analyzer.version,
    "description" → analyzer.description,
    "dataTypeList" → analyzer.dataTypeList,
    "cortexIds" → analyzer.cortexIds))
  val analyzerReads = Reads[Analyzer](json ⇒
    for {
      name ← (json \ "name").validate[String]
      version ← (json \ "version").validate[String]
      description ← (json \ "description").validate[String]
      dataTypeList ← (json \ "dataTypeList").validate[Seq[String]]
    } yield Analyzer(name, version, description, dataTypeList))
  implicit val analyzerFormats = Format(analyzerReads, analyzerWrites)

  val fileArtifactWrites = OWrites[FileArtifact](fileArtifact ⇒ Json.obj(
    "attributes" → fileArtifact.attributes))

  val fileArtifactReads = Reads[FileArtifact](json ⇒
    (json \ "attributes").validate[JsObject].map { attributes ⇒
      FileArtifact(Source.empty, attributes)
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
  val cortexJobReads = Reads[CortexJob](json ⇒
    for {
      id ← (json \ "id").validate[String]
      analyzerId ← (json \ "analyzerId").validate[String]
      artifact ← (json \ "artifact").validate[CortexArtifact]
      date ← (json \ "date").validate[Date]
      status ← (json \ "status").validate[JobStatus.Type]
    } yield CortexJob(id, analyzerId, artifact, date, status, Nil))
  val cortexJobWrites = Json.writes[CortexJob]
  implicit val cortexJobFormat = Format(cortexJobReads, cortexJobWrites)
  implicit val reportTypeFormat = enumFormat(ReportType)
}