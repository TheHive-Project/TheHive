package org.thp.thehive.controllers.v1

import java.util.Date

import play.api.libs.json.{JsObject, JsValue}

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Outputer
import org.thp.scalligraph.models.Entity
import org.thp.thehive.dto.v1._
import org.thp.thehive.models._
import org.thp.thehive.services.ProfileSrv

object Conversion {

  implicit class OutputOps[O, D](o: O)(implicit outputer: Outputer.Aux[O, D]) {
    def toJson: JsValue = outputer.toOutput(o).toJson
    def toOutput: D     = outputer.toOutput(o).toOutput
  }

  implicit val alertOutput: Outputer.Aux[RichAlert, OutputAlert] = Outputer[RichAlert, OutputAlert](
    _.into[OutputAlert]
      .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).toSet)
      .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
      .transform
  )

  implicit val auditOutput: Outputer.Aux[RichAudit, OutputAudit] = Outputer[RichAudit, OutputAudit](
    _.into[OutputAudit]
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

  implicit class InputAlertOps(inputAlert: InputAlert) {

    def toAlert: Alert =
      inputAlert
        .into[Alert]
        .withFieldComputed(_.severity, _.severity.getOrElse(2))
        .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
        .withFieldComputed(_.pap, _.pap.getOrElse(2))
        .withFieldConst(_.read, false)
        .withFieldConst(_.lastSyncDate, new Date)
        .withFieldConst(_.follow, true)
        .transform
  }

  implicit val caseOutput: Outputer.Aux[RichCase, OutputCase] = Outputer[RichCase, OutputCase](
    _.into[OutputCase]
      .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).toSet)
      .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
      .withFieldComputed(_.status, _.status.toString)
      .transform
  )

  implicit val caseWithStatsOutput: Outputer[(RichCase, JsObject)] =
    Outputer[(RichCase, JsObject), OutputCase](_._1.toOutput) // TODO add stats

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

  implicit val caseTemplateOutput: Outputer.Aux[RichCaseTemplate, OutputCaseTemplate] = Outputer[RichCaseTemplate, OutputCaseTemplate](
    _.into[OutputCaseTemplate]
      .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).toSet)
      .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
      .transform
  )

  implicit val richCustomFieldOutput: Outputer.Aux[RichCustomField, OutputCustomFieldValue] = Outputer[RichCustomField, OutputCustomFieldValue](
    _.into[OutputCustomFieldValue]
      .withFieldComputed(_.value, _.value.map(_.toString))
      .withFieldComputed(_.tpe, _.typeName)
      .transform
  )

  implicit val customFieldOutput: Outputer.Aux[CustomField with Entity, OutputCustomField] = Outputer[CustomField with Entity, OutputCustomField](
    _.asInstanceOf[CustomField]
      .into[OutputCustomField]
      .withFieldComputed(_.`type`, _.`type`.toString)
      .withFieldComputed(_.mandatory, _.mandatory)
      .transform
  )

  implicit class InputOrganisationOps(inputOrganisation: InputOrganisation) {

    def toOrganisation: Organisation =
      inputOrganisation
        .into[Organisation]
        .transform
  }

  implicit val organisationOutputer: Outputer.Aux[Organisation with Entity, OutputOrganisation] =
    Outputer[Organisation with Entity, OutputOrganisation](
      _.asInstanceOf[Organisation]
        .into[OutputOrganisation]
        .transform
    )

  implicit class InputTaskOps(inputTask: InputTask) {

    def toTask: Task =
      inputTask
        .into[Task]
        .withFieldComputed(_.status, _.status.fold(TaskStatus.Waiting)(TaskStatus.withName))
        .withFieldComputed(_.order, _.order.getOrElse(0))
        .withFieldComputed(_.group, _.group.getOrElse("default"))
        .transform
  }

  implicit val taskOutput: Outputer.Aux[RichTask, OutputTask] = Outputer[RichTask, OutputTask](
    _.into[OutputTask]
      .withFieldComputed(_.status, _.status.toString)
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
        .withFieldConst(_.totpSecret, None)
        //      .withFieldComputed(_.permissions, _.permissions.flatMap(Permissions.withName)) // FIXME unknown permissions are ignored
        .transform
  }

  implicit val userOutput: Outputer.Aux[RichUser, OutputUser] = Outputer[RichUser, OutputUser](
    _.into[OutputUser]
      .withFieldComputed(_.permissions, _.permissions.asInstanceOf[Set[String]])
      .withFieldComputed(_.hasKey, _.apikey.isDefined)
      .withFieldConst(_.organisations, Nil)
      .withFieldComputed(_.avatar, user => user.avatar.map(avatar => s"/api/v1/user/${user._id}/avatar/$avatar"))
      .transform
  )

  implicit val userWithOrganisationOutput: Outputer.Aux[(RichUser, Seq[(String, String)]), OutputUser] =
    Outputer[(RichUser, Seq[(String, String)]), OutputUser] { userWithOrganisations =>
      val (user, organisations) = userWithOrganisations
      user
        .into[OutputUser]
        .withFieldComputed(_.permissions, _.permissions.asInstanceOf[Set[String]])
        .withFieldComputed(_.hasKey, _.apikey.isDefined)
        .withFieldConst(_.organisations, organisations.map { case (org, role) => OutputOrganisationRole(org, role) })
        .withFieldComputed(_.avatar, user => user.avatar.map(avatar => s"/api/v1/user/${user._id}/avatar/$avatar"))
        .transform
    }

  implicit val profileOutput: Outputer.Aux[Profile with Entity, OutputProfile] = Outputer[Profile with Entity, OutputProfile](profile =>
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
      .withFieldComputed(_.permissions, _.permissions.asInstanceOf[Set[String]].toSeq.sorted) // Permission is String
      .withFieldComputed(_.editable, ProfileSrv.isEditable)
      .withFieldComputed(_.isAdmin, p => Permissions.containsRestricted(p.permissions))
      .transform
  )

  implicit val dashboardOutput: Outputer.Aux[RichDashboard, OutputDashboard] = Outputer[RichDashboard, OutputDashboard](dashboard =>
    dashboard
      .into[OutputDashboard]
      .withFieldConst(_._id, dashboard._id)
      .withFieldComputed(_.status, d => if (d.organisationShares.nonEmpty) "Shared" else "Private")
      .withFieldConst(_._type, "dashboard")
      .withFieldConst(_._updatedAt, dashboard._updatedAt)
      .withFieldConst(_._updatedBy, dashboard._updatedBy)
      .withFieldConst(_._createdAt, dashboard._createdAt)
      .withFieldConst(_._createdBy, dashboard._createdBy)
      .withFieldComputed(_.definition, _.definition.toString)
      .transform
  )

  implicit val attachmentOutput: Outputer.Aux[Attachment with Entity, OutputAttachment] = Outputer[Attachment with Entity, OutputAttachment](
    _.asInstanceOf[Attachment]
      .into[OutputAttachment]
      .withFieldComputed(_.hashes, _.hashes.map(_.toString).sortBy(_.length)(Ordering.Int.reverse))
      .withFieldComputed(_.id, _.attachmentId)
      .transform
  )

  implicit val observableOutput: Outputer.Aux[RichObservable, OutputObservable] = Outputer[RichObservable, OutputObservable](richObservable =>
    richObservable
      .into[OutputObservable]
      .withFieldConst(_._type, "case_artifact")
      .withFieldComputed(_._id, _.observable._id)
      .withFieldComputed(_._updatedAt, _.observable._updatedAt)
      .withFieldComputed(_._updatedBy, _.observable._updatedBy)
      .withFieldComputed(_._createdAt, _.observable._createdAt)
      .withFieldComputed(_._createdBy, _.observable._createdBy)
      .withFieldComputed(_.dataType, _.`type`.name)
      .withFieldComputed(_.startDate, _.observable._createdAt)
      .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
      .withFieldComputed(_.data, _.data.map(_.data))
      .withFieldComputed(_.attachment, _.attachment.map(_.toOutput))
      .withFieldConst(_.stats, JsObject.empty)
      .transform
  )

  implicit val logOutput: Outputer.Aux[RichLog, OutputLog] = Outputer[RichLog, OutputLog](richLog =>
    richLog
      .into[OutputLog]
      .withFieldConst(_._type, "case_task_log")
      .withFieldComputed(_._id, _._id)
      .withFieldComputed(_._updatedAt, _._updatedAt)
      .withFieldComputed(_._updatedBy, _._updatedBy)
      .withFieldComputed(_._createdAt, _._createdAt)
      .withFieldComputed(_._createdBy, _._createdBy)
      .withFieldComputed(_.message, _.message)
      .withFieldComputed(_.startDate, _._createdAt)
      .withFieldComputed(_.owner, _._createdBy)
      .withFieldComputed(_.status, l => if (l.deleted) "Deleted" else "Ok")
      .withFieldComputed(_.attachment, _.attachments.headOption.map(_.toOutput))
      .transform
  )
}
