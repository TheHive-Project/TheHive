package org.thp.thehive.controllers.v0

import java.util.Date

import play.api.libs.json.{JsObject, JsValue, Json}

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.controllers.Outputer
import org.thp.scalligraph.models.Entity
import org.thp.thehive.dto.v0._
import org.thp.thehive.models._

object Conversion {
  implicit class OutputOps[O, D](o: O)(implicit outputer: Outputer.Aux[O, D]) {
    def toJson: JsValue = outputer.toOutput(o).toJson
    def toOutput: D     = outputer.toOutput(o).toOutput
  }

  def actionToOperation(action: String): String = action match {
    case "create" => "Creation"
    case "update" => "Update"
    case "delete" => "Delete"
    case _        => "Unknown"
  }

  def fromObjectType(objectType: String): String = objectType match {
    //    case "Case" =>"case"
    case "Task"       => "case_task"
    case "Log"        => "case_task_log"
    case "Observable" => "case_artifact"
    case "Job"        => "case_artifact_job"
    case other        => other.toLowerCase()
  }

  def toObjectType(t: String): String = t match {
    case "case"          => "Case"
    case "case_artifact" => "Observable"
    case "case_task"     => "Task"
    case "case_task_log" => "Log"
    case "alert"         => "Alert"
  }

  implicit val alertOutput: Outputer.Aux[RichAlert, OutputAlert] = Outputer[RichAlert, OutputAlert](
    richAlert =>
      richAlert
        .into[OutputAlert]
        .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).toSet)
        .withFieldRenamed(_._createdAt, _.createdAt)
        .withFieldRenamed(_._createdBy, _.createdBy)
        .withFieldRenamed(_._id, _.id)
        .withFieldConst(_._type, "alert")
        .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
        .withFieldComputed(_.`case`, _.caseId)
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

  implicit val alertWithObservablesOutput: Outputer.Aux[(RichAlert, Seq[RichObservable]), OutputAlert] =
    Outputer[(RichAlert, Seq[RichObservable]), OutputAlert](
      richAlertWithObservables =>
        richAlertWithObservables
          ._1
          .into[OutputAlert]
          .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).toSet)
          .withFieldRenamed(_._id, _.id)
          .withFieldRenamed(_._createdAt, _.createdAt)
          .withFieldRenamed(_._createdBy, _.createdBy)
          .withFieldConst(_._type, "alert")
          .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
          .withFieldComputed(_.`case`, _.caseId)
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
          .withFieldConst(_.artifacts, richAlertWithObservables._2.map(_.toOutput))
          .transform
    )

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

  implicit val attachmentOutput: Outputer.Aux[Attachment with Entity, OutputAttachment] = Outputer[Attachment with Entity, OutputAttachment](
    _.asInstanceOf[Attachment]
      .into[OutputAttachment]
      .withFieldComputed(_.hashes, _.hashes.map(_.toString).sortBy(_.length)(Ordering.Int.reverse))
      .withFieldComputed(_.id, _.attachmentId)
      .transform
  )

  implicit val auditOutput: Outputer.Aux[RichAudit, OutputAudit] = Outputer[RichAudit, OutputAudit](
    _.into[OutputAudit]
      .withFieldComputed(_.operation, a => actionToOperation(a.action))
      .withFieldComputed(_.id, _._id)
      .withFieldComputed(_.createdAt, _._createdAt)
      .withFieldComputed(_.createdBy, _._createdBy)
      .withFieldConst(_._type, "audit")
      .withFieldComputed(_.`object`, _.`object`.map(OutputEntity.apply)) //objectToJson))
      .withFieldConst(_.base, true)
      .withFieldComputed(_.details, a => Json.parse(a.details.getOrElse("{}")).as[JsObject])
      .withFieldComputed(_.objectId, a => a.objectId.getOrElse(a.context._id))
      .withFieldComputed(_.objectType, a => fromObjectType(a.objectType.getOrElse(a.context._model.label)))
      .withFieldComputed(_.rootId, _.context._id)
      .withFieldComputed(_.startDate, _._createdAt)
      .withFieldComputed(
        _.summary,
        a => Map(fromObjectType(a.objectType.getOrElse(a.context._model.label)) -> Map(actionToOperation(a.action) -> 1))
      )
      .transform
  )

  implicit val caseOutput: Outputer.Aux[RichCase, OutputCase] = Outputer[RichCase, OutputCase](
    _.into[OutputCase]
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

  implicit val caseWithStatsOutput: Outputer.Aux[(RichCase, JsObject), OutputCase] = Outputer[(RichCase, JsObject), OutputCase](
    richCaseWithStats =>
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

  implicit class InputCaseTemplateOps(inputCaseTemplate: InputCaseTemplate) {

    def toCaseTemplate: CaseTemplate =
      inputCaseTemplate
        .into[CaseTemplate]
        .withFieldComputed(_.displayName, _.displayName.getOrElse(""))
        .withFieldComputed(_.flag, _.flag.getOrElse(false))
        .transform
  }

  implicit val caseTemplateOutput: Outputer.Aux[RichCaseTemplate, OutputCaseTemplate] = Outputer[RichCaseTemplate, OutputCaseTemplate](
    _.into[OutputCaseTemplate]
      .withFieldComputed(_.customFields, rc => JsObject(rc.customFields.map(cf => cf.name -> Json.obj(cf.typeName -> cf.toJson))))
      .withFieldRenamed(_._id, _.id)
      .withFieldRenamed(_._updatedAt, _.updatedAt)
      .withFieldRenamed(_._updatedBy, _.updatedBy)
      .withFieldRenamed(_._createdAt, _.createdAt)
      .withFieldRenamed(_._createdBy, _.createdBy)
      .withFieldConst(_.status, "Ok")
      .withFieldConst(_._type, "caseTemplate")
      .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
      .withFieldComputed(_.tasks, _.tasks.map(t => RichTask(t, None).toOutput))
      .withFieldConst(_.metrics, JsObject.empty)
      .transform
  )

  implicit val richCustomFieldOutput: Outputer.Aux[RichCustomField, OutputCustomFieldValue] = Outputer[RichCustomField, OutputCustomFieldValue](
    _.into[OutputCustomFieldValue]
      .withFieldComputed(_.value, _.value.map {
        case d: Date => d.getTime.toString
        case other   => other.toString
      })
      .withFieldComputed(_.tpe, _.typeName)
      .transform
  )

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

  implicit val customFieldOutput: Outputer.Aux[CustomField with Entity, OutputCustomField] = Outputer[CustomField with Entity, OutputCustomField](
    customField =>
      customField
        .asInstanceOf[CustomField]
        .into[OutputCustomField]
        .withFieldComputed(_.`type`, _.`type`.toString)
        .withFieldComputed(_.reference, _.name)
        .withFieldComputed(_.name, _.displayName)
        .withFieldConst(_.id, customField._id)
        .transform
  )

  implicit val dashboardOutput: Outputer.Aux[Dashboard with Entity, OutputDashboard] = Outputer[Dashboard with Entity, OutputDashboard](
    dashboard =>
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

  implicit class InputDashboardOps(inputDashboard: InputDashboard) {

    def toDashboard: Dashboard =
      inputDashboard
        .into[Dashboard]
        .withFieldComputed(_.shared, _.status == "Shared")
        .transform
  }

  implicit val logOutput: Outputer.Aux[RichLog, OutputLog] = Outputer[RichLog, OutputLog](
    richLog =>
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
        .withFieldComputed(_.attachment, _.attachments.headOption.map(_.toOutput))
        .transform
  )

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

  implicit val observableOutput: Outputer.Aux[RichObservable, OutputObservable] = Outputer[RichObservable, OutputObservable](
    _.into[OutputObservable]
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
      .withFieldComputed(_.attachment, _.attachment.map(_.toOutput))
      .withFieldConst(_.stats, JsObject.empty)
      .transform
  )

  implicit val observableWithStatsOutput: Outputer.Aux[(RichObservable, JsObject), OutputObservable] =
    Outputer[(RichObservable, JsObject), OutputObservable](
      richObservableWithStats =>
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
          .withFieldComputed(_.attachment, _.attachment.map(_.toOutput))
          .withFieldConst(_.stats, richObservableWithStats._2)
          .transform
    )

  implicit class InputOrganisationOps(inputOrganisation: InputOrganisation) {

    def toOrganisation: Organisation =
      inputOrganisation
        .into[Organisation]
        .transform
  }

  implicit val organisationOutput: Outputer.Aux[Organisation with Entity, OutputOrganisation] =
    Outputer[Organisation with Entity, OutputOrganisation](
      organisation =>
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

  implicit val profileOutput: Outputer.Aux[Profile with Entity, OutputProfile] = Outputer[Profile with Entity, OutputProfile](
    profile =>
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

  implicit class InputProfileObs(inputProfile: InputProfile) {

    def toProfile: Profile =
      inputProfile
        .into[Profile]
        .withFieldComputed(_.permissions, _.permissions.map(Permission.apply))
        .transform
  }

  implicit val shareOutput: Outputer.Aux[RichShare, OutputShare] = Outputer[RichShare, OutputShare](
    _.into[OutputShare]
      .withFieldComputed(_._id, _.share._id)
      .withFieldComputed(_.createdAt, _.share._createdAt)
      .withFieldComputed(_.createdBy, _.share._createdBy)
      .transform
  )

  implicit val tagOutput: Outputer.Aux[Tag with Entity, OutputTag] = Outputer[Tag with Entity, OutputTag](
    _.asInstanceOf[Tag]
      .into[OutputTag]
      .transform
  )

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

  implicit val taskOutput: Outputer.Aux[RichTask, OutputTask] = Outputer[RichTask, OutputTask](
    _.into[OutputTask]
      .withFieldRenamed(_._id, _.id)
      .withFieldComputed(_.status, _.status.toString)
      .withFieldConst(_._type, "case_task")
      .withFieldRenamed(_._updatedAt, _.updatedAt)
      .withFieldRenamed(_._updatedBy, _.updatedBy)
      .withFieldRenamed(_._createdAt, _.createdAt)
      .withFieldRenamed(_._createdBy, _.createdBy)
      .transform
  )

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

  implicit val userOutput: Outputer.Aux[RichUser, OutputUser] = Outputer[RichUser, OutputUser](
    _.into[OutputUser]
      .withFieldComputed(_.roles, u => permissions2Roles(u.permissions))
      .withFieldRenamed(_.login, _.id)
      .withFieldConst(_.hasKey, None)
      .withFieldComputed(_.status, u => if (u.locked) "Locked" else "Ok")
      .withFieldRenamed(_._createdBy, _.createdBy)
      .withFieldRenamed(_._createdAt, _.createdAt)
      .withFieldRenamed(_._updatedBy, _.updatedBy)
      .withFieldRenamed(_._updatedAt, _.updatedAt)
      .withFieldConst(_._type, "user")
      .transform
  )

  val userWithKeyInfoOutput: Outputer.Aux[RichUser, OutputUser] =
    Outputer[RichUser, OutputUser](u => u.toOutput.copy(hasKey = Some(u.apikey.isDefined)))

  val adminPermissions: Set[Permission] = Set(Permissions.manageUser, Permissions.manageOrganisation)

  def permissions2Roles(permissions: Set[Permission]): Set[String] = {
    val roles =
      if ((permissions & adminPermissions).nonEmpty) Set("admin", "write", "read", "alert")
      else if ((permissions - Permissions.manageAlert).nonEmpty) Set("write", "read")
      else Set("read")
    if (permissions.contains(Permissions.manageAlert)) roles + "alert"
    else roles
  }

}
