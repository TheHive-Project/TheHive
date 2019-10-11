package org.thp.thehive.controllers.v0

import java.util.Date

import play.api.libs.json.{JsObject, JsValue, Json}

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.Entity
import org.thp.thehive.dto.v0._
import org.thp.thehive.models._

object Conversion {

  def actionToOperation(action: String): String = action match {
    case "create" => "Creation"
    case "update" => "Update"
    case "delete" => "Delete"
    case _        => "Unknown"
  }

  def objectTypeMapper(objectType: String): String = objectType match {
    //    case "Case" =>"case"
    case "Task"       => "case_task"
    case "Log"        => "case_task_log"
    case "Observable" => "case_artifact"
    case "Job"        => "case_artifact_job"
    case other        => other.toLowerCase()
  }

  implicit class AlertOps(richAlert: RichAlert) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputAlert] =
      Output(
        richAlert
          .into[OutputAlert]
          .withFieldComputed(_.customFields, _.customFields.map(_.toOutput.toOutput).toSet)
          .withFieldRenamed(_._createdAt, _.createdAt)
          .withFieldRenamed(_._createdBy, _.createdBy)
          .withFieldRenamed(_._id, _.id)
          .withFieldConst(_._type, "alert")
          .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
          .withFieldComputed(
            _.status,
            alert =>
              (alert.caseId, alert.read) match {
                case (None, true)  => "Ignored"
                case (None, false) => "New"
                case (_, true)     => "Imported"
                case (_, false)    => "Updated"
              }
          )
          .transform
      )
  }

  implicit class AlertWithObservablesOps(richAlertWithObservables: (RichAlert, Seq[RichObservable])) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputAlert] =
      Output(
        richAlertWithObservables
          ._1
          .into[OutputAlert]
          .withFieldComputed(_.customFields, _.customFields.map(_.toOutput.toOutput).toSet)
          .withFieldRenamed(_._id, _.id)
          .withFieldRenamed(_._createdAt, _.createdAt)
          .withFieldRenamed(_._createdBy, _.createdBy)
          .withFieldConst(_._type, "alert")
          .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
          .withFieldComputed(
            _.status,
            alert =>
              (alert.caseId, alert.read) match {
                case (None, true)  => "Ignored"
                case (None, false) => "New"
                case (_, true)     => "Imported"
                case (_, false)    => "Updated"
              }
          )
          .withFieldConst(_.artifacts, richAlertWithObservables._2.map(_.toOutput.toOutput))
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

  implicit class AttachmentOps(attachment: Attachment) {

    def toOutput: Output[OutputAttachment] =
      Output[OutputAttachment](
        attachment
          .into[OutputAttachment]
          .withFieldComputed(_.hashes, _.hashes.map(_.toString).sortBy(_.length)(Ordering.Int.reverse))
          .withFieldComputed(_.id, _.attachmentId)
          .transform
      )
  }

  implicit class AuditOps(audit: RichAudit) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputAudit] =
      Output[OutputAudit](
        audit
          .into[OutputAudit]
          .withFieldComputed(_.operation, a => actionToOperation(a.action))
          .withFieldComputed(_.id, _._id)
          .withFieldComputed(_.createdAt, _._createdAt)
          .withFieldComputed(_.createdBy, _._createdBy)
          .withFieldConst(_._type, "audit")
          .withFieldComputed(_.`object`, _.`object`.map(OutputEntity.apply)) //objectToJson))
          .withFieldConst(_.base, true)
          .withFieldComputed(_.details, a => Json.parse(a.details.getOrElse("{}")).as[JsObject])
          .withFieldComputed(_.objectId, a => a.objectId.getOrElse(a.context._id))
          .withFieldComputed(_.objectType, a => objectTypeMapper(a.objectType.getOrElse(a.context._model.label)))
          .withFieldComputed(_.rootId, _.context._id)
          .withFieldComputed(_.startDate, _._createdAt)
          .withFieldComputed(
            _.summary,
            a => Map(objectTypeMapper(a.objectType.getOrElse(a.context._model.label)) -> Map(actionToOperation(a.action) -> 1))
          )
          .transform
      )
  }

  implicit class caseOps(richCase: RichCase) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputCase] =
      Output[OutputCase](
        richCase
          .into[OutputCase]
          .withFieldComputed(_.customFields, rc => JsObject(rc.customFields.map(cf => cf.name -> Json.obj(cf.typeName -> cf.toJson))))
          .withFieldComputed(_.status, _.status.toString)
          .withFieldConst(_._type, "case")
          .withFieldComputed(_.id, _._id)
          .withFieldRenamed(_.number, _.caseId)
          .withFieldRenamed(_.user, _.owner)
          .withFieldRenamed(_._updatedAt, _.updatedAt)
          .withFieldRenamed(_._updatedBy, _.updatedBy)
          .withFieldRenamed(_._createdAt, _.createdAt)
          .withFieldRenamed(_._createdBy, _.createdBy)
          .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
          .withFieldConst(_.stats, JsObject.empty)
          .withFieldComputed(_.permissions, _.userPermissions.map(_.toString))
          .transform
      )
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

  implicit class CaseWithStatsOps(richCaseWithStats: (RichCase, JsObject)) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputCase] =
      Output[OutputCase](
        richCaseWithStats
          ._1
          .into[OutputCase]
          .withFieldComputed(_.customFields, rc => JsObject(rc.customFields.map(cf => cf.name -> Json.obj(cf.typeName -> cf.toJson))))
          .withFieldComputed(_.status, _.status.toString)
          .withFieldConst(_._type, "case")
          .withFieldComputed(_.id, _._id)
          .withFieldRenamed(_.number, _.caseId)
          .withFieldRenamed(_.user, _.owner)
          .withFieldRenamed(_._updatedAt, _.updatedAt)
          .withFieldRenamed(_._updatedBy, _.updatedBy)
          .withFieldRenamed(_._createdAt, _.createdAt)
          .withFieldRenamed(_._createdBy, _.createdBy)
          .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
          .withFieldConst(_.stats, richCaseWithStats._2)
          .withFieldComputed(_.permissions, _.userPermissions.map(_.toString))
          .transform
      )
  }

  implicit class RichCustomFieldOps(customFieldValue: RichCustomField) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputCustomFieldValue] =
      Output[OutputCustomFieldValue](
        customFieldValue
          .into[OutputCustomFieldValue]
          .withFieldComputed(_.value, _.value.map {
            case d: Date => d.getTime.toString
            case other   => other.toString
          })
          .withFieldComputed(_.tpe, _.typeName)
          .transform
      )
  }

  implicit class InputCustomFieldOps(inputCustomField: InputCustomField) {

    def toCustomField: CustomField =
      inputCustomField
        .into[CustomField]
        .withFieldComputed(_.`type`, icf => CustomFieldType.withName(icf.`type`))
        .withFieldComputed(_.mandatory, _.mandatory.getOrElse(false))
        .withFieldComputed(_.name, _.reference)
        .withFieldComputed(_.displayName, _.name)
        .transform
  }

  implicit class CustomFieldOps(customField: CustomField with Entity) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputCustomField] =
      Output[OutputCustomField](
        customField
          .asInstanceOf[CustomField]
          .into[OutputCustomField]
          .withFieldComputed(_.`type`, _.`type`.toString)
          .withFieldComputed(_.reference, _.name)
          .withFieldComputed(_.name, _.displayName)
          .withFieldConst(_.id, customField._id)
          .transform
      )
  }

  implicit class DashboardOps(dashboard: Dashboard with Entity) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputDashboard] =
      Output[OutputDashboard](
        dashboard
          .asInstanceOf[Dashboard]
          .into[OutputDashboard]
          .withFieldConst(_.id, dashboard._id)
          .withFieldConst(_._id, dashboard._id)
          .withFieldComputed(_.status, d => if (d.shared) "Shared" else "Private")
          .withFieldConst(_._type, "dashboard")
          .withFieldConst(_.updatedAt, dashboard._updatedAt)
          .withFieldConst(_.updatedBy, dashboard._updatedBy)
          .withFieldConst(_.createdAt, dashboard._createdAt)
          .withFieldConst(_.createdBy, dashboard._createdBy)
          .transform
      )
  }

  implicit class InputDashboardOps(inputDashboard: InputDashboard) {

    def toDashboard: Dashboard =
      inputDashboard
        .into[Dashboard]
        .withFieldComputed(_.shared, _.status == "Shared")
        .transform
  }

  implicit class LogOps(richLog: RichLog) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputLog] =
      Output[OutputLog](
        richLog
          .into[OutputLog]
          .withFieldConst(_._type, "case_task_log")
          .withFieldComputed(_.id, _._id)
          .withFieldComputed(_._id, _._id)
          .withFieldComputed(_.updatedAt, _._updatedAt)
          .withFieldComputed(_.updatedBy, _._updatedBy)
          .withFieldComputed(_.createdAt, _._createdAt)
          .withFieldComputed(_.createdBy, _._createdBy)
          .withFieldComputed(_.message, _.message)
          .withFieldComputed(_.startDate, _._createdAt)
          .withFieldComputed(_.owner, _._createdBy)
          .withFieldComputed(_.status, l => if (l.deleted) "Deleted" else "Ok")
          .withFieldComputed(_.attachment, _.attachments.headOption.map(_.toOutput.toOutput))
          .transform
      )
  }

  implicit class InputLogOps(inputLog: InputLog) {

    def toLog: Log =
      inputLog
        .into[Log]
        .withFieldConst(_.date, new Date)
        .withFieldConst(_.deleted, false)
        .transform
  }

  implicit class InputObservableOps(inputObservable: InputObservable) {

    def toObservable: Observable =
      inputObservable
        .into[Observable]
        .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
        .withFieldComputed(_.ioc, _.ioc.getOrElse(false))
        .withFieldComputed(_.sighted, _.sighted.getOrElse(false))
        .transform
  }

  implicit class ObservableOps(richObservable: RichObservable) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputObservable] =
      Output[OutputObservable](
        richObservable
          .into[OutputObservable]
          .withFieldConst(_._type, "case_artifact")
          .withFieldComputed(_.id, _.observable._id)
          .withFieldComputed(_._id, _.observable._id)
          .withFieldComputed(_.updatedAt, _.observable._updatedAt)
          .withFieldComputed(_.updatedBy, _.observable._updatedBy)
          .withFieldComputed(_.createdAt, _.observable._createdAt)
          .withFieldComputed(_.createdBy, _.observable._createdBy)
          .withFieldComputed(_.dataType, _.`type`.name)
          .withFieldComputed(_.startDate, _.observable._createdAt)
          .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
          .withFieldComputed(_.data, _.data.map(_.data))
          .withFieldComputed(_.attachment, _.attachment.map(_.toOutput.toOutput))
          .withFieldConst(_.stats, JsObject.empty)
          .transform
      )
  }

  implicit class ObservableWithStatsOps(richObservableWithStats: (RichObservable, JsObject)) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputObservable] =
      Output[OutputObservable](
        richObservableWithStats
          ._1
          .into[OutputObservable]
          .withFieldConst(_._type, "case_artifact")
          .withFieldComputed(_.id, _.observable._id)
          .withFieldComputed(_._id, _.observable._id)
          .withFieldComputed(_.updatedAt, _.observable._updatedAt)
          .withFieldComputed(_.updatedBy, _.observable._updatedBy)
          .withFieldComputed(_.createdAt, _.observable._createdAt)
          .withFieldComputed(_.createdBy, _.observable._createdBy)
          .withFieldComputed(_.dataType, _.`type`.name)
          .withFieldComputed(_.startDate, _.observable._createdAt)
          .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
          .withFieldComputed(_.data, _.data.map(_.data))
          .withFieldComputed(_.attachment, _.attachment.map(_.toOutput.toOutput))
          .withFieldConst(_.stats, richObservableWithStats._2)
          .transform
      )
  }

  implicit class InputOrganisationOps(inputOrganisation: InputOrganisation) {

    def toOrganisation: Organisation =
      inputOrganisation
        .into[Organisation]
        .transform
  }

  implicit class OrganisationObs(organisation: Organisation with Entity) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputOrganisation] =
      Output(
        OutputOrganisation(
          organisation.name,
          organisation.description,
          organisation._id,
          organisation._id,
          organisation._createdAt,
          organisation._createdBy,
          organisation._updatedAt,
          organisation._updatedBy,
          "organisation"
        )
      )
  }

  implicit class ProfileObs(profile: Profile with Entity) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputProfile] =
      Output[OutputProfile](
        profile
          .asInstanceOf[Profile]
          .into[OutputProfile]
          .withFieldConst(_._id, profile._id)
          .withFieldConst(_.id, profile._id)
          .withFieldConst(_.updatedAt, profile._updatedAt)
          .withFieldConst(_.updatedBy, profile._updatedBy)
          .withFieldConst(_.createdAt, profile._createdAt)
          .withFieldConst(_.createdBy, profile._createdBy)
          .withFieldConst(_._type, "profile")
          .withFieldComputed(_.permissions, _.permissions.asInstanceOf[Set[String]].toSeq.sorted)
          .transform
      )
  }

  implicit class InputProfileObs(inputProfile: InputProfile) {

    def toProfile: Profile =
      inputProfile
        .into[Profile]
        .withFieldComputed(_.permissions, _.permissions.map(Permission.apply))
        .transform
  }

  implicit class ShareOps(share: RichShare) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputShare] =
      Output[OutputShare](
        share
          .into[OutputShare]
          .withFieldComputed(_._id, _.share._id)
          .withFieldComputed(_.createdAt, _.share._createdAt)
          .withFieldComputed(_.createdBy, _.share._createdBy)
          .transform
      )
  }

  implicit class TagOps(tag: Tag with Entity) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputTag] =
      Output[OutputTag](
        tag
          .asInstanceOf[Tag]
          .into[OutputTag]
          .transform
      )
  }

  implicit class InputTaskOps(inputTask: InputTask) {

    def toTask: Task =
      inputTask
        .into[Task]
        .withFieldComputed(_.status, _.status.fold(TaskStatus.Waiting)(TaskStatus.withName))
        .withFieldComputed(_.order, _.order.getOrElse(0))
        .withFieldComputed(_.flag, _.flag.getOrElse(false))
        .withFieldComputed(_.group, _.group.getOrElse("default"))
        .transform
  }

  implicit class RichTaskOps(richTask: RichTask) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputTask] =
      Output[OutputTask](
        richTask
          .into[OutputTask]
          .withFieldConst(_.id, richTask._id)
          .withFieldComputed(_.status, _.status.toString)
          .withFieldConst(_._type, "case_task")
          .withFieldRenamed(_._updatedAt, _.updatedAt)
          .withFieldRenamed(_._updatedBy, _.updatedBy)
          .withFieldRenamed(_._createdAt, _.createdAt)
          .withFieldRenamed(_._createdBy, _.createdBy)
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
        //    .withFieldRenamed(_.roles, _.permissions)
        .transform
  }

  implicit class UserOps(user: RichUser) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputUser] = toOutput(withKeyInfo = true)

    val adminPermissions: Set[Permission] = Set(Permissions.manageUser, Permissions.manageOrganisation)

    def permissions2Roles(permissions: Set[Permission]): Set[String] = {
      val roles =
        if ((permissions & adminPermissions).nonEmpty) Set("admin", "write", "read", "alert")
        else if ((permissions - Permissions.manageAlert).nonEmpty) Set("write", "read")
        else Set("read")
      if (permissions.contains(Permissions.manageAlert)) roles + "alert"
      else roles
    }

    def toOutput(withKeyInfo: Boolean): Output[OutputUser] =
      Output[OutputUser](
        user
          .into[OutputUser]
          .withFieldComputed(_.roles, u => permissions2Roles(u.permissions))
          .withFieldRenamed(_.login, _.id)
          .withFieldComputed(_.hasKey, u => if (withKeyInfo) Some(u.apikey.isDefined) else None)
          .withFieldComputed(_.status, u => if (u.locked) "Locked" else "Ok")
          .withFieldRenamed(_._createdBy, _.createdBy)
          .withFieldRenamed(_._createdAt, _.createdAt)
          .withFieldRenamed(_._updatedBy, _.updatedBy)
          .withFieldRenamed(_._updatedAt, _.updatedAt)
          .withFieldConst(_._type, "user")
          .transform
      )
  }
}
