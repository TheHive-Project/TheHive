package models

import java.nio.file.Path

import play.api.libs.json.{ JsString, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.Writes

import org.elastic4play.models.JsonFormat.enumFormat

object JsonFormat {
  implicit val userStatusFormat = enumFormat(UserStatus)
  implicit val caseStatusFormat = enumFormat(CaseStatus)
  implicit val caseResolutionStatusFormat = enumFormat(CaseResolutionStatus)
  implicit val caseImpactStatusFormat = enumFormat(CaseImpactStatus)
  implicit val artifactStatusFormat = enumFormat(ArtifactStatus)
  implicit val jobStatusFormat = enumFormat(JobStatus)
  implicit val taskStatusFormat = enumFormat(TaskStatus)
  implicit val logStatusFormat = enumFormat(LogStatus)
  implicit val caseTemplateStatusFormat = enumFormat(CaseTemplateStatus)

  implicit val pathWrites: Writes[Path] = Writes((value: Path) => JsString(value.toString))

  implicit val analyzerWrites: Writes[Analyzer] = Writes((analyzer: Analyzer) =>
    Json.obj(
      "name" -> analyzer.name,
      "version" -> analyzer.version,
      "description" -> analyzer.description,
      "dataTypeList" -> analyzer.dataTypeList,
      "id" -> analyzer.id))
}