package models

import java.nio.file.Path

import play.api.libs.json._

import org.elastic4play.models.JsonFormat.enumFormat
import org.elastic4play.services.Role

object JsonFormat {
  implicit val userStatusFormat: Format[UserStatus.Type]                     = enumFormat(UserStatus)
  implicit val caseStatusFormat: Format[CaseStatus.Type]                     = enumFormat(CaseStatus)
  implicit val caseResolutionStatusFormat: Format[CaseResolutionStatus.Type] = enumFormat(CaseResolutionStatus)
  implicit val caseImpactStatusFormat: Format[CaseImpactStatus.Type]         = enumFormat(CaseImpactStatus)
  implicit val artifactStatusFormat: Format[ArtifactStatus.Type]             = enumFormat(ArtifactStatus)
  implicit val taskStatusFormat: Format[TaskStatus.Type]                     = enumFormat(TaskStatus)
  implicit val logStatusFormat: Format[LogStatus.Type]                       = enumFormat(LogStatus)
  implicit val caseTemplateStatusFormat: Format[CaseTemplateStatus.Type]     = enumFormat(CaseTemplateStatus)
  implicit val alertStatusFormat: Format[AlertStatus.Type]                   = enumFormat(AlertStatus)
  implicit val dashboardStatusFormat: Format[DashboardStatus.Type]           = enumFormat(DashboardStatus)

  implicit val pathWrites: Writes[Path] = Writes((value: Path) => JsString(value.toString))

  private val roleWrites: Writes[Role] = Writes((role: Role) => JsString(role.name.toLowerCase()))
  private val roleReads: Reads[Role] = Reads {
    case JsString(s) if Roles.isValid(s) => JsSuccess(Roles.withName(s).get)
    case _                               => JsError(Seq(JsPath -> Seq(JsonValidationError(s"error.expected.role(${Roles.roleNames}"))))
  }
  implicit val roleFormat: Format[Role] = Format[Role](roleReads, roleWrites)
}
