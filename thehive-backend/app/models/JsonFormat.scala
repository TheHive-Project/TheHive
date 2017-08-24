package models

import java.nio.file.Path

import play.api.libs.json.{ Format, JsString, Writes }

import org.elastic4play.models.JsonFormat.enumFormat

object JsonFormat {
  implicit val userStatusFormat: Format[UserStatus.Type] = enumFormat(UserStatus)
  implicit val caseStatusFormat: Format[CaseStatus.Type] = enumFormat(CaseStatus)
  implicit val caseResolutionStatusFormat: Format[CaseResolutionStatus.Type] = enumFormat(CaseResolutionStatus)
  implicit val caseImpactStatusFormat: Format[CaseImpactStatus.Type] = enumFormat(CaseImpactStatus)
  implicit val artifactStatusFormat: Format[ArtifactStatus.Type] = enumFormat(ArtifactStatus)
  implicit val taskStatusFormat: Format[TaskStatus.Type] = enumFormat(TaskStatus)
  implicit val logStatusFormat: Format[LogStatus.Type] = enumFormat(LogStatus)
  implicit val caseTemplateStatusFormat: Format[CaseTemplateStatus.Type] = enumFormat(CaseTemplateStatus)
  implicit val alertStatusFormat: Format[AlertStatus.Type] = enumFormat(AlertStatus)

  implicit val pathWrites: Writes[Path] = Writes((value: Path) ⇒ JsString(value.toString))
}