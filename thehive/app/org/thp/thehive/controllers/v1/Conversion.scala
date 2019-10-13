package org.thp.thehive.controllers.v1

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.Entity
import org.thp.thehive.dto.v1._
import org.thp.thehive.models._
import play.api.libs.json.{JsObject, JsValue}

object Conversion {

  implicit class AlertOps(richAlert: RichAlert) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputAlert] =
      Output[OutputAlert](
        richAlert
          .into[OutputAlert]
          .withFieldComputed(_.customFields, _.customFields.map(_.toOutput.toOutput).toSet)
          .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
          .transform
      )
  }

  implicit class AuditOps(audit: RichAudit) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputAudit] =
      Output[OutputAudit](
        audit
          .into[OutputAudit]
          .withFieldComputed(_.operation, _.action)
          .withFieldComputed(_._id, _._id)
          .withFieldComputed(_._createdAt, _._createdAt)
          .withFieldComputed(_._createdBy, _._createdBy)
          .withFieldComputed(_.obj, a => a.`object`.map(OutputEntity.apply))
          //        .withFieldComputed(_.obj, a ⇒ OutputEntity(a.obj))
          //        .withFieldComputed(
          //          _.summary,
          //          _.summary.mapValues(
          //            opCount ⇒
          //              opCount.map {
          //                case (op, count) ⇒ op.toString → count
          //              }
          //          )
          .withFieldConst(_.attributeName, None) // FIXME
          .withFieldConst(_.oldValue, None)
          .withFieldConst(_.newValue, None)
          .withFieldConst(_.summary, Map.empty[String, Map[String, Int]])
          .transform
      )
  }
  implicit class InputAlertOps(inputAlert: InputAlert) {

    def toAlert: Alert =
      inputAlert
        .into[Alert]
        .withFieldComputed(_.severity, _.severity.getOrElse(2))
        .withFieldComputed(_.flag, _.flag.getOrElse(false))
        .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
        .withFieldComputed(_.pap, _.pap.getOrElse(2))
        .withFieldConst(_.read, false)
        .withFieldConst(_.lastSyncDate, new Date)
        .withFieldConst(_.follow, true)
        .transform
  }

  implicit class CaseOps(richCase: RichCase) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputCase] =
      Output[OutputCase](
        richCase
          .into[OutputCase]
          .withFieldComputed(_.customFields, _.customFields.map(_.toOutput.toOutput).toSet)
          .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
          .withFieldComputed(_.status, _.status.toString)
          .transform
      )
  }

  implicit class CaseWithStatsOps(richCaseWithStats: (RichCase, JsObject)) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputCase] =
      richCaseWithStats._1.toOutput // TODO add stats
  }

  implicit class InputCaseOps(inputCase: InputCase) {

    def toCase: Case =
      inputCase
        .into[Case]
        .withFieldComputed(_.severity, _.severity.getOrElse(2))
        .withFieldComputed(_.startDate, _.startDate.getOrElse(new Date))
        .withFieldComputed(_.flag, _.flag.getOrElse(false))
        .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
        .withFieldComputed(_.pap, _.pap.getOrElse(2))
        .withFieldConst(_.status, CaseStatus.Open)
        .withFieldConst(_.number, 0)
        .transform

    def withCaseTemplate(caseTemplate: RichCaseTemplate): InputCase =
      InputCase(
        title = caseTemplate.titlePrefix.getOrElse("") + inputCase.title,
        description = inputCase.description,
        severity = caseTemplate.severity orElse inputCase.severity,
        startDate = inputCase.startDate,
        endDate = inputCase.endDate,
        tags = inputCase.tags,
        flag = inputCase.flag orElse Some(caseTemplate.flag),
        tlp = inputCase.tlp orElse caseTemplate.tlp,
        pap = inputCase.pap orElse caseTemplate.pap,
        status = inputCase.status,
        summary = inputCase.summary orElse caseTemplate.summary,
        user = inputCase.user,
        customFieldValue = inputCase.customFieldValue
      )
  }

  implicit class InputCaseTemplateOps(inputCaseTemplate: InputCaseTemplate) {

    def toCaseTemplate: CaseTemplate =
      inputCaseTemplate
        .into[CaseTemplate]
        .withFieldComputed(_.displayName, _.displayName.getOrElse(""))
        .withFieldComputed(_.flag, _.flag.getOrElse(false))
        .transform
  }

  implicit class CaseTemplateOps(richCaseTemplate: RichCaseTemplate) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputCaseTemplate] =
      Output[OutputCaseTemplate](
        richCaseTemplate
          .into[OutputCaseTemplate]
          .withFieldComputed(_.customFields, _.customFields.map(_.toOutput.toOutput).toSet)
          .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
          .transform
      )
  }

  implicit class RichCustomFieldOps(customFieldValue: RichCustomField) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputCustomFieldValue] =
      Output[OutputCustomFieldValue](
        customFieldValue
          .into[OutputCustomFieldValue]
          .withFieldComputed(_.value, _.value.map(_.toString))
          .withFieldComputed(_.tpe, _.typeName)
          .transform
      )
  }

  implicit class CustomFieldOps(customField: CustomField with Entity) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputCustomField] =
      Output[OutputCustomField](
        customField
          .asInstanceOf[CustomField]
          .into[OutputCustomField]
          .withFieldComputed(_.`type`, _.`type`.toString)
          .withFieldComputed(_.mandatory, _.mandatory)
          .transform
      )
  }

  implicit class InputOrganisationOps(inputOrganisation: InputOrganisation) {

    def toOrganisation: Organisation =
      inputOrganisation
        .into[Organisation]
        .transform
  }

  implicit class OrganisationOps(organisation: Organisation with Entity) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputOrganisation] =
      Output[OutputOrganisation](
        organisation
          .asInstanceOf[Organisation]
          .into[OutputOrganisation]
          .transform
      )
  }

  implicit class InputTaskOps(inputTask: InputTask) {

    def toTask: Task =
      inputTask
        .into[Task]
        .withFieldComputed(_.status, _.status.fold(TaskStatus.Waiting)(TaskStatus.withName))
        .withFieldComputed(_.order, _.order.getOrElse(0))
        .withFieldComputed(_.group, _.group.getOrElse("default"))
        .transform
  }

  implicit class TaskOps(task: RichTask) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputTask] =
      Output[OutputTask](
        task
          .into[OutputTask]
          .withFieldComputed(_.status, _.status.toString)
          .transform
      )
  }

  implicit class InputUserOps(inputUser: InputUser) {

    def toUser: User =
      inputUser
        .into[User]
        .withFieldComputed(_.id, _.login)
        .withFieldConst(_.apikey, None)
        .withFieldConst(_.password, None)
        .withFieldConst(_.locked, false)
        //      .withFieldComputed(_.permissions, _.permissions.flatMap(Permissions.withName)) // FIXME unknown permissions are ignored
        .transform
  }

  implicit class UserOps(user: RichUser) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputUser] =
      Output[OutputUser](
        user
          .into[OutputUser]
          .withFieldComputed(_.permissions, _.permissions.asInstanceOf[Set[String]])
          .withFieldComputed(_.hasKey, _.apikey.isDefined)
          //        .withFieldComputed(_.permissions, _.permissions)
          //        .withFieldConst(_.permissions, Set.empty[String]
          .transform
      )
  }
}
