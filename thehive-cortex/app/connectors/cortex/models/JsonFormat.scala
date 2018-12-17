package connectors.cortex.models

import akka.stream.scaladsl.Source
import play.api.libs.json._
import play.api.libs.json.Json.toJsFieldJsValueWrapper

import org.elastic4play.models.JsonFormat.enumFormat
import java.util.Date

object JsonFormat {
  private val analyzerWrites = Writes[Analyzer](analyzer ⇒ Json.obj(
    "id" → analyzer.id,
    "name" → analyzer.name,
    "version" → analyzer.version,
    "description" → analyzer.description,
    "dataTypeList" → analyzer.dataTypeList,
    "cortexIds" → analyzer.cortexIds))
  private val analyzerReads = Reads[Analyzer](json ⇒
    for {
      name ← (json \ "name").validate[String]
      version ← (json \ "version").validate[String]
      definition = (name + "_" + version).replaceAll("\\.", "_")
      id = (json \ "id").asOpt[String].getOrElse(definition)
      renamed = if (id == definition) definition else name
      description ← (json \ "description").validate[String]
      dataTypeList ← (json \ "dataTypeList").validate[Seq[String]]
    } yield Analyzer(id, renamed, version, description, dataTypeList))
  implicit val analyzerFormat: Format[Analyzer] = Format(analyzerReads, analyzerWrites)

  private val fileArtifactWrites = OWrites[FileArtifact](fileArtifact ⇒ Json.obj(
    "attributes" → fileArtifact.attributes))

  private val fileArtifactReads = Reads[FileArtifact](json ⇒
    (json \ "attributes").validate[JsObject].map { attributes ⇒
      FileArtifact(Source.empty, attributes)
    })
  private val fileArtifactFormat = OFormat(fileArtifactReads, fileArtifactWrites)
  private val dataArtifactFormat = Json.format[DataArtifact]
  private val artifactReads = Reads[CortexArtifact](json ⇒
    json.validate[JsObject].flatMap {
      case a if a.keys.contains("data") ⇒ json.validate[DataArtifact](dataArtifactFormat)
      case _                            ⇒ json.validate[FileArtifact](fileArtifactFormat)
    })
  private val artifactWrites = OWrites[CortexArtifact] {
    case dataArtifact: DataArtifact ⇒ dataArtifactFormat.writes(dataArtifact)
    case fileArtifact: FileArtifact ⇒ fileArtifactWrites.writes(fileArtifact)
  }

  implicit val artifactFormat: OFormat[CortexArtifact] = OFormat(artifactReads, artifactWrites)
  implicit val jobStatusFormat: Format[JobStatus.Type] = enumFormat(JobStatus)

  private def filterObject(json: JsObject, attributes: String*): JsObject = {
    JsObject(attributes.flatMap(a ⇒ (json \ a).asOpt[JsValue].map(a → _)))
  }

  implicit val cortexJobReads: Reads[CortexJob] = Reads[CortexJob](json ⇒
    for {
      id ← (json \ "id").validate[String]
      analyzerId ← (json \ "workerId").orElse(json \ "analyzerId").validate[String]
      analyzerName = (json \ "workerName").orElse(json \ "analyzerName").validate[String].getOrElse(analyzerId)
      analyzerDefinition = (json \ "workerDefinitionId").orElse(json \ "analyzerDefinitionId").validate[String].getOrElse(analyzerId)
      attributes = filterObject(json.as[JsObject], "tlp", "message", "parameters")
      artifact = (json \ "artifact").validate[CortexArtifact]
        .getOrElse {
          (json \ "data").asOpt[String].map(DataArtifact(_, attributes))
            .getOrElse(FileArtifact(Source.empty, attributes))
        }
      date ← (json \ "date").validate[Date]
      status ← (json \ "status").validate[JobStatus.Type]
    } yield CortexJob(id, analyzerId, analyzerName, analyzerDefinition, artifact, date, status))

  implicit val reportTypeFormat: Format[ReportType.Type] = enumFormat(ReportType)

  private val responderWrites = Writes[Responder](responder ⇒ Json.obj(
    "id" → responder.id,
    "name" → responder.name,
    "version" → responder.version,
    "description" → responder.description,
    "dataTypeList" → responder.dataTypeList,
    "maxTlp" → responder.maxTlp,
    "maxPap" → responder.maxPap,
    "cortexIds" → responder.cortexIds))
  private val responderReads = Reads[Responder](json ⇒
    for {
      name ← (json \ "name").validate[String]
      version ← (json \ "version").validate[String]
      definition = (name + "_" + version).replaceAll("\\.", "_")
      id = (json \ "id").asOpt[String].getOrElse(definition)
      renamed = if (id == definition) definition else name
      description ← (json \ "description").validate[String]
      dataTypeList ← (json \ "dataTypeList").validate[Seq[String]]
      maxTlp ← (json \ "maxTlp").validateOpt[Long]
      maxPap ← (json \ "maxPap").validateOpt[Long]
    } yield Responder(id, renamed, version, description, dataTypeList, maxTlp, maxPap))
  implicit val responderFormat: Format[Responder] = Format(responderReads, responderWrites)
}