package org.thp.thehive.controllers.v1

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Renderer
import org.thp.scalligraph.models.Entity
import org.thp.thehive.dto.v1._
import org.thp.thehive.models._
import play.api.libs.json.{JsObject, JsValue, Json}

object Conversion {

  implicit class RendererOps[O, D](o: O)(implicit renderer: Renderer.Aux[O, D]) {
    def toJson: JsValue = renderer.toOutput(o).toJson
    def toOutput: D     = renderer.toOutput(o).toValue
  }

  implicit val alertOutput: Renderer.Aux[RichAlert, OutputAlert] = Renderer.json[RichAlert, OutputAlert](
    _.into[OutputAlert]
      .withFieldConst(_._type, "Alert")
      .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).toSet)
      .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
      .withFieldConst(_.extraData, JsObject.empty)
      .transform
  )

  implicit val alertWithStatsOutput: Renderer[(RichAlert, JsObject)] =
    Renderer.json[(RichAlert, JsObject), OutputAlert] { alertWithExtraData =>
      alertWithExtraData
        ._1
        .into[OutputAlert]
        .withFieldConst(_._type, "Alert")
        .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).toSet)
        .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
        .withFieldConst(_.extraData, alertWithExtraData._2)
        .transform
    }

  implicit val auditOutput: Renderer.Aux[RichAudit, OutputAudit] = Renderer.json[RichAudit, OutputAudit](
    _.into[OutputAudit]
      .withFieldComputed(_.operation, _.action)
      .withFieldComputed(_._id, _._id)
      .withFieldConst(_._type, "Audit")
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

  implicit val caseOutput: Renderer.Aux[RichCase, OutputCase] = Renderer.json[RichCase, OutputCase](
    _.into[OutputCase]
      .withFieldConst(_._type, "Case")
      .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).toSet)
      .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
      .withFieldComputed(_.status, _.status.toString)
      .withFieldConst(_.extraData, JsObject.empty)
      .transform
  )

  implicit val caseWithStatsOutput: Renderer[(RichCase, JsObject)] =
    Renderer.json[(RichCase, JsObject), OutputCase] { caseWithExtraData =>
      caseWithExtraData
        ._1
        .into[OutputCase]
        .withFieldConst(_._type, "Case")
        .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).toSet)
        .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
        .withFieldComputed(_.status, _.status.toString)
        .withFieldConst(_.extraData, caseWithExtraData._2)
        .transform
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
        severity = inputCase.severity orElse caseTemplate.severity,
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

  implicit val caseTemplateOutput: Renderer.Aux[RichCaseTemplate, OutputCaseTemplate] = Renderer.json[RichCaseTemplate, OutputCaseTemplate](
    _.into[OutputCaseTemplate]
      .withFieldConst(_._type, "CaseTemplate")
      .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).toSet)
      .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
      .transform
  )

  implicit val richCustomFieldOutput: Renderer.Aux[RichCustomField, OutputCustomFieldValue] = Renderer.json[RichCustomField, OutputCustomFieldValue](
    _.into[OutputCustomFieldValue]
      .withFieldComputed(_.value, _.jsValue)
      .withFieldComputed(_.`type`, _.typeName)
      .withFieldComputed(_.order, _.order.getOrElse(0))
      .transform
  )

  implicit val customFieldOutput: Renderer.Aux[CustomField with Entity, OutputCustomField] =
    Renderer.json[CustomField with Entity, OutputCustomField](
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

  implicit val richOrganisationRenderer: Renderer.Aux[RichOrganisation, OutputOrganisation] =
    Renderer.json[RichOrganisation, OutputOrganisation](organisation =>
      organisation
        .into[OutputOrganisation]
        .withFieldConst(_._type, "Organisation")
        .withFieldConst(_.name, organisation.name)
        .withFieldConst(_.description, organisation.description)
        .withFieldComputed(_.links, _.links.map(_.name))
        .transform
    )

  implicit val organiastionRenderer: Renderer.Aux[Organisation with Entity, OutputOrganisation] =
    Renderer.json[Organisation with Entity, OutputOrganisation](organisation =>
      OutputOrganisation(
        organisation._id,
        "organisation",
        organisation._createdBy,
        organisation._updatedBy,
        organisation._createdAt,
        organisation._updatedAt,
        organisation.name,
        organisation.description,
        Nil
      )
    )

  implicit class InputTaskOps(inputTask: InputTask) {

    def toTask: Task =
      inputTask
        .into[Task]
        .withFieldComputed(_.status, _.status.fold(TaskStatus.Waiting)(TaskStatus.withName))
        .withFieldComputed(_.order, _.order.getOrElse(0))
        .withFieldComputed(_.group, _.group.getOrElse("default"))
        .withFieldComputed(_.flag, _.flag.getOrElse(false))
        .transform
  }

  implicit val taskOutput: Renderer.Aux[RichTask, OutputTask] = Renderer.json[RichTask, OutputTask](
    _.into[OutputTask]
      .withFieldConst(_._type, "Task")
      .withFieldComputed(_.status, _.status.toString)
      .withFieldComputed(_.assignee, _.assignee.map(_.login))
      .withFieldConst(_.extraData, JsObject.empty)
      .transform
  )

  implicit val taskWithStatsOutput: Renderer[(RichTask, JsObject)] =
    Renderer.json[(RichTask, JsObject), OutputTask] { taskWithExtraData =>
      taskWithExtraData
        ._1
        .into[OutputTask]
        .withFieldConst(_._type, "Task")
        .withFieldComputed(_.status, _.status.toString)
        .withFieldComputed(_.assignee, _.assignee.map(_.login))
        .withFieldConst(_.extraData, taskWithExtraData._2)
        .transform
    }

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

  implicit val userOutput: Renderer.Aux[RichUser, OutputUser] = Renderer.json[RichUser, OutputUser](
    _.into[OutputUser]
      .withFieldComputed(_.permissions, _.permissions.asInstanceOf[Set[String]])
      .withFieldComputed(_.hasKey, _.apikey.isDefined)
      .withFieldConst(_.organisations, Nil)
      .withFieldComputed(_.avatar, user => user.avatar.map(avatar => s"/api/v1/user/${user._id}/avatar/$avatar"))
      .transform
  )

  implicit val userWithOrganisationOutput: Renderer.Aux[(RichUser, Seq[(String, String)]), OutputUser] =
    Renderer.json[(RichUser, Seq[(String, String)]), OutputUser] { userWithOrganisations =>
      val (user, organisations) = userWithOrganisations
      user
        .into[OutputUser]
        .withFieldComputed(_.permissions, _.permissions.asInstanceOf[Set[String]])
        .withFieldComputed(_.hasKey, _.apikey.isDefined)
        .withFieldConst(_.organisations, organisations.map { case (org, role) => OutputOrganisationProfile(org, role) })
        .withFieldComputed(_.avatar, user => user.avatar.map(avatar => s"/api/v1/user/${user._id}/avatar/$avatar"))
        .transform
    }

  implicit val profileOutput: Renderer.Aux[Profile with Entity, OutputProfile] = Renderer.json[Profile with Entity, OutputProfile](profile =>
    profile
      .asInstanceOf[Profile]
      .into[OutputProfile]
      .withFieldConst(_._id, profile._id)
      .withFieldConst(_._updatedAt, profile._updatedAt)
      .withFieldConst(_._updatedBy, profile._updatedBy)
      .withFieldConst(_._createdAt, profile._createdAt)
      .withFieldConst(_._createdBy, profile._createdBy)
      .withFieldConst(_._type, "Profile")
      .withFieldComputed(_.permissions, _.permissions.asInstanceOf[Set[String]].toSeq.sorted) // Permission is String
      .withFieldComputed(_.editable, _.isEditable)
      .withFieldComputed(_.isAdmin, p => Permissions.containsRestricted(p.permissions))
      .transform
  )

  implicit val dashboardOutput: Renderer.Aux[RichDashboard, OutputDashboard] = Renderer.json[RichDashboard, OutputDashboard](dashboard =>
    dashboard
      .into[OutputDashboard]
      .withFieldConst(_._id, dashboard._id)
      .withFieldComputed(_.status, d => if (d.organisationShares.nonEmpty) "Shared" else "Private")
      .withFieldConst(_._type, "Dashboard")
      .withFieldConst(_._updatedAt, dashboard._updatedAt)
      .withFieldConst(_._updatedBy, dashboard._updatedBy)
      .withFieldConst(_._createdAt, dashboard._createdAt)
      .withFieldConst(_._createdBy, dashboard._createdBy)
      .withFieldComputed(_.definition, _.definition.toString)
      .transform
  )

  implicit val attachmentOutput: Renderer.Aux[Attachment with Entity, OutputAttachment] = Renderer.json[Attachment with Entity, OutputAttachment](
    _.asInstanceOf[Attachment]
      .into[OutputAttachment]
      .withFieldComputed(_.hashes, _.hashes.map(_.toString).sortBy(_.length)(Ordering.Int.reverse))
      .withFieldComputed(_.id, _.attachmentId)
      .transform
  )

  implicit class InputObservableOps(inputObservable: InputObservable) {
    def toObservable: Observable =
      inputObservable
        .into[Observable]
        .withFieldComputed(_.ioc, _.ioc.getOrElse(false))
        .withFieldComputed(_.sighted, _.sighted.getOrElse(false))
        .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
        .transform
  }
  implicit val observableOutput: Renderer.Aux[RichObservable, OutputObservable] = Renderer.json[RichObservable, OutputObservable](richObservable =>
    richObservable
      .into[OutputObservable]
      .withFieldConst(_._type, "Observable")
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
      .withFieldComputed(
        _.reports,
        a =>
          JsObject(a.reportTags.groupBy(_.origin).map {
            case (origin, tags) =>
              origin -> Json.obj(
                "taxonomies" -> tags
                  .map(t => Json.obj("level" -> t.level.toString, "namespace" -> t.namespace, "predicate" -> t.predicate, "value" -> t.value))
              )
          })
      )
      .withFieldConst(_.extraData, JsObject.empty)
      .transform
  )

  implicit val observableWithExtraData: Renderer.Aux[(RichObservable, JsObject), OutputObservable] =
    Renderer.json[(RichObservable, JsObject), OutputObservable] {
      case (richObservable, extraData) =>
        richObservable
          .into[OutputObservable]
          .withFieldConst(_._type, "case_artifact")
          .withFieldComputed(_.dataType, _.`type`.name)
          .withFieldComputed(_.startDate, _.observable._createdAt)
          .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
          .withFieldComputed(_.data, _.data.map(_.data))
          .withFieldComputed(_.attachment, _.attachment.map(_.toOutput))
          .withFieldComputed(
            _.reports,
            a =>
              JsObject(a.reportTags.groupBy(_.origin).map {
                case (origin, tags) =>
                  origin -> Json.obj(
                    "taxonomies" -> tags
                      .map(t => Json.obj("level" -> t.level.toString, "namespace" -> t.namespace, "predicate" -> t.predicate, "value" -> t.value))
                  )
              })
          )
          .withFieldConst(_.extraData, extraData)
          .transform
    }

  implicit val logOutput: Renderer.Aux[RichLog, OutputLog] = Renderer.json[RichLog, OutputLog](richLog =>
    richLog
      .into[OutputLog]
      .withFieldConst(_._type, "Log")
      .withFieldRenamed(_._createdAt, _.date)
      .withFieldComputed(_.attachment, _.attachments.headOption.map(_.toOutput))
      .withFieldRenamed(_._createdBy, _.owner)
      .withFieldConst(_.extraData, JsObject.empty)
      .transform
  )

  implicit val logWithStatsOutput: Renderer[(RichLog, JsObject)] =
    Renderer.json[(RichLog, JsObject), OutputLog] { logWithExtraData =>
      logWithExtraData
        ._1
        .into[OutputLog]
        .withFieldConst(_._type, "Log")
        .withFieldRenamed(_._createdAt, _.date)
        .withFieldComputed(_.attachment, _.attachments.headOption.map(_.toOutput))
        .withFieldRenamed(_._createdBy, _.owner)
        .withFieldConst(_.extraData, logWithExtraData._2)
        .transform
    }

  implicit class InputLogOps(inputLog: InputLog) {

    def toLog: Log =
      inputLog
        .into[Log]
        .withFieldConst(_.date, new Date)
        .withFieldConst(_.deleted, false)
        .transform
  }

}
