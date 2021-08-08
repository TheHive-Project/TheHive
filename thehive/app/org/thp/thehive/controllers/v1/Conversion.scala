package org.thp.thehive.controllers.v1

import eu.timepit.refined.api.Refined
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.EntityId
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{FFile, Renderer}
import org.thp.scalligraph.models.Entity
import org.thp.thehive.dto.{Description, MultiLineString512, Pap, Severity, String128, String16, String32, String512, String64, Tlp}
import org.thp.thehive.dto.v1.{InputTaxonomy, OutputTaxonomy, _}
import org.thp.thehive.models._
import org.thp.thehive.services.{SharingParameter, SharingProfile, SharingRule}
import play.api.libs.json.{JsObject, JsValue, Json}

import java.nio.file.Paths
import java.util.Date

object Conversion {
  implicit def refinedTransformer[T, P]: Transformer[Refined[T, P], T] = _.value

  implicit class RendererOps[V, O](v: V)(implicit renderer: Renderer.Aux[V, O]) {
    def toJson: JsValue = renderer.toOutput(v).toJson
    def toOutput: O     = renderer.toOutput(v).toValue
  }

  implicit val alertOutput: Renderer.Aux[RichAlert, OutputAlert] = Renderer.toJson[RichAlert, OutputAlert](
    _.into[OutputAlert]
      .withFieldConst(_._type, "Alert")
      .withFieldComputed(_._id, _._id.toString)
      .withFieldComputed(_.caseId, _.caseId.map(_.toString))
      .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).sortBy(_.order))
      .withFieldComputed(_.tags, _.tags.toSet)
      .withFieldConst(_.extraData, JsObject.empty)
      .enableMethodAccessors
      .transform
  )

  implicit val alertWithStatsOutput: Renderer.Aux[(RichAlert, JsObject), OutputAlert] =
    Renderer.toJson[(RichAlert, JsObject), OutputAlert] { alertWithExtraData =>
      alertWithExtraData
        ._1
        .into[OutputAlert]
        .withFieldConst(_._type, "Alert")
        .withFieldComputed(_._id, _._id.toString)
        .withFieldComputed(_.caseId, _.caseId.map(_.toString))
        .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).sortBy(_.order))
        .withFieldComputed(_.tags, _.tags.toSet)
        .withFieldConst(_.extraData, alertWithExtraData._2)
        .enableMethodAccessors
        .transform
    }

  implicit val auditOutput: Renderer.Aux[RichAudit, OutputAudit] = Renderer.toJson[RichAudit, OutputAudit](
    _.into[OutputAudit]
      .withFieldComputed(_.operation, _.action)
      .withFieldComputed(_._id, _._id.toString)
      .withFieldConst(_._type, "Audit")
      .withFieldComputed(_._createdAt, _._createdAt)
      .withFieldComputed(_._createdBy, _._createdBy)
      .withFieldComputed(_.obj, a => a.`object`.map(OutputEntity.apply))
      //        .withFieldComputed(_.obj, a => OutputEntity(a.obj))
      //        .withFieldComputed(
      //          _.summary,
      //          _.summary.mapValues(
      //            opCount =>
      //              opCount.map {
      //                case (op, count) => op.toString → count
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
        .withFieldComputed(_.severity, _.severity.fold(2)(_.value))
        .withFieldComputed(_.tlp, _.tlp.fold(2)(_.value))
        .withFieldComputed(_.pap, _.pap.fold(2)(_.value))
        .withFieldConst(_.read, false)
        .withFieldConst(_.lastSyncDate, new Date)
        .withFieldConst(_.follow, true)
        .withFieldConst(_.tags, inputAlert.tags.toSeq.map(_.value))
        .withFieldConst(_.caseId, EntityId.empty)
        .transform
  }

  implicit val caseOutput: Renderer.Aux[RichCase, OutputCase] = Renderer.toJson[RichCase, OutputCase](
    _.into[OutputCase]
      .withFieldConst(_._type, "Case")
      .withFieldComputed(_._id, _._id.toString)
      .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).sortBy(_.order))
      .withFieldComputed(_.tags, _.tags.toSet)
      .withFieldComputed(_.status, _.status.toString)
      .withFieldConst(_.extraData, JsObject.empty)
      .withFieldComputed(_.assignee, _.assignee)
      .enableMethodAccessors
      .transform
  )

  implicit val caseWithStatsOutput: Renderer.Aux[(RichCase, JsObject), OutputCase] =
    Renderer.toJson[(RichCase, JsObject), OutputCase] { caseWithExtraData =>
      caseWithExtraData
        ._1
        .into[OutputCase]
        .withFieldConst(_._type, "Case")
        .withFieldComputed(_._id, _._id.toString)
        .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).sortBy(_.order))
        .withFieldComputed(_.tags, _.tags.toSet)
        .withFieldComputed(_.status, _.status.toString)
        .withFieldConst(_.extraData, caseWithExtraData._2)
        .withFieldComputed(_.assignee, _.assignee)
        .enableMethodAccessors
        .transform
    }

  implicit class InputCaseOps(inputCase: InputCase) {

    def toCase(implicit authContext: AuthContext): Case =
      inputCase
        .into[Case]
        .withFieldComputed(_.severity, _.severity.fold(2)(_.value))
        .withFieldComputed(_.startDate, _.startDate.getOrElse(new Date))
        .withFieldComputed(_.flag, _.flag.getOrElse(false))
        .withFieldComputed(_.tlp, _.tlp.fold(2)(_.value))
        .withFieldComputed(_.pap, _.pap.fold(2)(_.value))
        .withFieldConst(_.status, CaseStatus.Open)
        .withFieldConst(_.number, 0)
        .withFieldComputed(_.tags, _.tags.toSeq.map(_.value))
        .withFieldComputed(_.assignee, c => Some(c.user.fold(authContext.userId)(_.value)))
        .withFieldConst(_.impactStatus, None)
        .withFieldConst(_.resolutionStatus, None)
        .transform

    def withCaseTemplate(caseTemplate: RichCaseTemplate): InputCase =
      InputCase(
        title = String512("title", caseTemplate.titlePrefix.getOrElse("") + inputCase.title.value),
        description = inputCase.description,
        severity = inputCase.severity orElse caseTemplate.severity.map(Severity.apply),
        startDate = inputCase.startDate,
        endDate = inputCase.endDate,
        tags = inputCase.tags,
        flag = inputCase.flag orElse Some(caseTemplate.flag),
        tlp = inputCase.tlp orElse caseTemplate.tlp.map(Tlp.apply),
        pap = inputCase.pap orElse caseTemplate.pap.map(Pap.apply),
        status = inputCase.status,
        summary = inputCase.summary orElse caseTemplate.summary.map(Description("summary", _)),
        user = inputCase.user,
        customFieldValues = inputCase.customFieldValues
      )
  }

  implicit class RichCaseOps(`case`: RichCase) {
    def toInputCase: InputCase =
      InputCase(
        title = String512("title", `case`.title),
        description = Description("description", `case`.description),
        severity = Some(Severity.apply(`case`.severity)),
        startDate = Some(`case`.startDate),
        endDate = `case`.endDate,
        tags = `case`.tags.map(String128.apply("tag", _)).toSet,
        flag = Some(`case`.flag),
        tlp = Some(Tlp(`case`.tlp)),
        pap = Some(Pap(`case`.pap)),
        status = Some(String16("status", `case`.status.toString)),
        summary = `case`.summary.map(Description("summary", _)),
        user = `case`.assignee.map(String128("user", _)),
        customFieldValues = `case`
          .customFields
          .map(cf =>
            InputCustomFieldValue(
              name = String64("customFieldValues.name", cf.name),
              value = cf.jsValue,
              order = cf.order
            )
          )
      )
  }

  implicit class InputCaseTemplateOps(inputCaseTemplate: InputCaseTemplate) {

    def toCaseTemplate: CaseTemplate =
      inputCaseTemplate
        .into[CaseTemplate]
        .withFieldComputed(_.displayName, _.displayName.fold("")(_.value)) // FIXME empty string ?
        .withFieldComputed(_.flag, _.flag.getOrElse(false))
        .transform
  }

  implicit val caseTemplateOutput: Renderer.Aux[RichCaseTemplate, OutputCaseTemplate] = Renderer.toJson[RichCaseTemplate, OutputCaseTemplate](
    _.into[OutputCaseTemplate]
      .withFieldConst(_._type, "CaseTemplate")
      .withFieldComputed(_._id, _._id.toString)
      .withFieldComputed(_.customFields, _.customFields.map(_.toOutput).sortBy(_.order))
      .withFieldComputed(_.tags, _.tags.toSet)
      .withFieldComputed(_.tasks, _.tasks.map(_.toOutput))
      .enableMethodAccessors
      .transform
  )

  implicit val richCustomFieldOutput: Renderer.Aux[RichCustomField, OutputCustomFieldValue] =
    Renderer.toJson[RichCustomField, OutputCustomFieldValue](
      _.into[OutputCustomFieldValue]
        .withFieldComputed(_._id, _.customFieldValue._id.toString)
        .withFieldComputed(_.value, _.jsValue)
        .withFieldComputed(_.`type`, _.typeName)
        .withFieldComputed(_.order, _.order.getOrElse(0))
        .enableMethodAccessors
        .transform
    )

  implicit class InputCustomFieldOps(inputCustomField: InputCustomField) {

    def toCustomField: CustomField =
      inputCustomField
        .into[CustomField]
        .withFieldComputed(_.`type`, icf => CustomFieldType.withName(icf.`type`.value))
        .withFieldComputed(_.mandatory, _.mandatory.getOrElse(false))
        .withFieldComputed(_.displayName, c => c.displayName.getOrElse(c.name).value)
        .transform
  }

  implicit val customFieldOutput: Renderer.Aux[CustomField with Entity, OutputCustomField] =
    Renderer.toJson[CustomField with Entity, OutputCustomField](customField =>
      customField
        .asInstanceOf[CustomField]
        .into[OutputCustomField]
        .withFieldConst(_._id, customField._id.toString)
        .withFieldConst(_._type, "CustomField")
        .withFieldConst(_._createdAt, customField._createdAt)
        .withFieldConst(_._createdBy, customField._createdBy)
        .withFieldConst(_._updatedAt, customField._updatedAt)
        .withFieldConst(_._updatedBy, customField._updatedBy)
        .withFieldComputed(_.`type`, _.`type`.toString)
        .withFieldComputed(_.mandatory, _.mandatory)
        .transform
    )

  implicit class InputOrganisationOps(inputOrganisation: InputOrganisation) {

    def toOrganisation: Organisation =
      inputOrganisation
        .into[Organisation]
        .withFieldComputed(_.taskRule, _.taskRule.fold(SharingRule.default)(_.value))
        .withFieldComputed(_.observableRule, _.observableRule.fold(SharingRule.default)(_.value))
        .transform
  }

  implicit val richOrganisationRenderer: Renderer.Aux[RichOrganisation, OutputOrganisation] =
    Renderer.toJson[RichOrganisation, OutputOrganisation](organisation =>
      organisation
        .into[OutputOrganisation]
        .withFieldComputed(_._id, _._id.toString)
        .withFieldConst(_._type, "Organisation")
        .withFieldConst(_.name, organisation.name)
        .withFieldConst(_.description, organisation.description)
        .withFieldComputed(_.links, _.links.map(ol => OrganisationLink(ol._1.name, ol._2._1, ol._2._2)).toSeq)
        .enableMethodAccessors
        .transform
    )

  implicit val organisationRenderer: Renderer.Aux[Organisation with Entity, OutputOrganisation] =
    Renderer.toJson[Organisation with Entity, OutputOrganisation](organisation =>
      OutputOrganisation(
        organisation._id.toString,
        "organisation",
        organisation._createdBy,
        organisation._updatedBy,
        organisation._createdAt,
        organisation._updatedAt,
        organisation.name,
        organisation.description,
        organisation.taskRule,
        organisation.observableRule,
        Seq.empty
      )
    )

  implicit class InputTaskOps(inputTask: InputTask) {

    def toTask: Task =
      inputTask
        .into[Task]
        .withFieldComputed(_.status, _.status.fold(TaskStatus.Waiting)(s => TaskStatus.withName(s.value)))
        .withFieldComputed(_.order, _.order.getOrElse(0))
        .withFieldComputed(_.group, _.group.fold("default")(_.value))
        .withFieldComputed(_.flag, _.flag.getOrElse(false))
        .transform
  }

  implicit class TaksOps(task: Task) {
    def toInputTask: InputTask =
      InputTask(
        title = String128("title", task.title),
        group = Some(String32("group", task.group)),
        description = task.description.map(Description("description", _)),
        status = Some(String16("status", task.status.toString)),
        flag = Some(task.flag),
        startDate = task.startDate,
        endDate = task.endDate,
        order = Some(task.order),
        dueDate = task.dueDate,
        assignee = task.assignee.map(String128("assignee", _))
      )
  }

  implicit val taskOutput: Renderer.Aux[RichTask, OutputTask] = Renderer.toJson[RichTask, OutputTask](
    _.into[OutputTask]
      .withFieldConst(_._type, "Task")
      .withFieldComputed(_._id, _._id.toString)
      .withFieldComputed(_.status, _.status.toString)
      .withFieldConst(_.extraData, JsObject.empty)
      .enableMethodAccessors
      .transform
  )

  implicit val taskWithStatsOutput: Renderer.Aux[(RichTask, JsObject), OutputTask] =
    Renderer.toJson[(RichTask, JsObject), OutputTask] { taskWithExtraData =>
      taskWithExtraData
        ._1
        .into[OutputTask]
        .withFieldConst(_._type, "Task")
        .withFieldComputed(_._id, _._id.toString)
        .withFieldComputed(_.status, _.status.toString)
        .withFieldConst(_.extraData, taskWithExtraData._2)
        .enableMethodAccessors
        .transform
    }

  implicit class InputTaxonomyOps(inputTaxonomy: InputTaxonomy) {

    def toTaxonomy: Taxonomy =
      inputTaxonomy
        .into[Taxonomy]
        .transform
  }

  implicit val taxonomyOutput: Renderer.Aux[RichTaxonomy, OutputTaxonomy] =
    Renderer.toJson[RichTaxonomy, OutputTaxonomy](
      _.into[OutputTaxonomy]
        .withFieldComputed(_._id, _._id.toString)
        .withFieldConst(_._type, "Taxonomy")
        .withFieldComputed(_.tags, _.tags.map(_.toOutput))
        .withFieldConst(_.extraData, JsObject.empty)
        .enableMethodAccessors
        .transform
    )

  implicit val taxonomyWithStatsOutput: Renderer.Aux[(RichTaxonomy, JsObject), OutputTaxonomy] =
    Renderer.toJson[(RichTaxonomy, JsObject), OutputTaxonomy] { taxoWithExtraData =>
      taxoWithExtraData
        ._1
        .into[OutputTaxonomy]
        .withFieldComputed(_._id, _._id.toString)
        .withFieldConst(_._type, "Taxonomy")
        .withFieldComputed(_.tags, _.tags.map(_.toOutput))
        .withFieldConst(_.extraData, taxoWithExtraData._2)
        .enableMethodAccessors
        .transform
    }

  implicit val tagOutput: Renderer.Aux[Tag with Entity, OutputTag] =
    Renderer.toJson[Tag with Entity, OutputTag](tag =>
      tag
        .asInstanceOf[Tag]
        .into[OutputTag]
        .withFieldConst(_._id, tag._id.toString)
        .withFieldConst(_._updatedAt, tag._updatedAt)
        .withFieldConst(_._updatedBy, tag._updatedBy)
        .withFieldConst(_._createdAt, tag._createdAt)
        .withFieldConst(_._createdBy, tag._createdBy)
        .withFieldConst(_._type, "Tag")
        .withFieldComputed(_.namespace, t => if (t.isFreeTag) "_freetags_" else t.namespace)
        .withFieldConst(_.extraData, JsObject.empty)
        .transform
    )

  implicit val tagWithStatsOutput: Renderer.Aux[(Tag with Entity, JsObject), OutputTag] =
    Renderer.toJson[(Tag with Entity, JsObject), OutputTag] {
      case (tag, stats) =>
        tag
          .asInstanceOf[Tag]
          .into[OutputTag]
          .withFieldConst(_._id, tag._id.toString)
          .withFieldConst(_._updatedAt, tag._updatedAt)
          .withFieldConst(_._updatedBy, tag._updatedBy)
          .withFieldConst(_._createdAt, tag._createdAt)
          .withFieldConst(_._createdBy, tag._createdBy)
          .withFieldConst(_._type, "Tag")
          .withFieldComputed(_.namespace, t => if (t.isFreeTag) "_freetags_" else t.namespace)
          .withFieldConst(_.extraData, stats)
          .transform
    }

  implicit class InputUserOps(inputUser: InputUser) {

    def toUser: User =
      inputUser
        .into[User]
        .withFieldComputed(_.id, _.login.value)
        .withFieldConst(_.apikey, None)
        .withFieldConst(_.resetSecret, None)
        .withFieldConst(_.password, None)
        .withFieldConst(_.locked, false)
        .withFieldConst(_.totpSecret, None)
        //      .withFieldComputed(_.permissions, _.permissions.flatMap(Permissions.withName)) // FIXME unknown permissions are ignored
        .transform
  }

  implicit val userOutput: Renderer.Aux[RichUser, OutputUser] = Renderer.toJson[RichUser, OutputUser](
    _.into[OutputUser]
      .withFieldComputed(_.permissions, _.permissions.asInstanceOf[Set[String]])
      .withFieldComputed(_.hasKey, _.apikey.isDefined)
      .withFieldComputed(_._id, _._id.toString)
      .withFieldConst(_.organisations, Nil)
      .withFieldComputed(_.avatar, user => user.avatar.map(avatar => s"/api/v1/user/${user._id}/avatar/$avatar"))
      .enableMethodAccessors
      .transform
  )

  implicit val userWithOrganisationOutput: Renderer.Aux[(RichUser, Seq[(Organisation with Entity, String)]), OutputUser] =
    Renderer.toJson[(RichUser, Seq[(Organisation with Entity, String)]), OutputUser] { userWithOrganisations =>
      val (user, organisations) = userWithOrganisations
      user
        .into[OutputUser]
        .withFieldComputed(_._id, _._id.toString)
        .withFieldComputed(_.permissions, _.permissions.asInstanceOf[Set[String]])
        .withFieldComputed(_.hasKey, _.apikey.isDefined)
        .withFieldConst(_.organisations, organisations.map { case (org, role) => OutputOrganisationProfile(org._id.toString, org.name, role) })
        .withFieldComputed(_.avatar, user => user.avatar.map(avatar => s"/api/v1/user/${user._id}/avatar/$avatar"))
        .enableMethodAccessors
        .transform
    }

  implicit val sharingProfileOutput: Renderer.Aux[SharingProfile, OutputSharingProfile] = Renderer.toJson[SharingProfile, OutputSharingProfile](
    _.into[OutputSharingProfile].transform
  )

  implicit val shareOutput: Renderer.Aux[RichShare, OutputShare] = Renderer.toJson[RichShare, OutputShare](
    _.into[OutputShare]
      .withFieldComputed(_._id, _.share._id.toString)
      .withFieldConst(_._type, "Share")
      .withFieldComputed(_.caseId, _.caseId.toString)
      .enableMethodAccessors
      .transform
  )

  implicit class InputSharingParameterOps(inputSharingParameter: InputShare) {
    def toSharingParameter: (String, SharingParameter) =
      if (inputSharingParameter.share.getOrElse(true))
        inputSharingParameter.organisation.value -> inputSharingParameter
          .into[SharingParameter]
          .withFieldConst(_.autoShare, true)
          .withFieldComputed(_.permissionProfile, _.profile.fold(Profile.analyst.name)(_.value))
          .withFieldComputed(_.taskRule, _.taskRule.fold(SharingRule.default)(_.value))
          .withFieldComputed(_.observableRule, _.observableRule.fold(SharingRule.default)(_.value))
          .transform
      else
        inputSharingParameter.organisation.value -> SharingParameter(
          autoShare = false,
          permissionProfile = Profile.readonly.name,
          taskRule = SharingRule.default,
          observableRule = SharingRule.default
        )

    def toSharingProfile(defaultTaskRule: String, defaultObservableRule: String): SharingProfile =
      SharingProfile(
        name = "fromParameter",
        description = "Sharing profile from parameter",
        autoShare = inputSharingParameter.share.getOrElse(true),
        editable = true,
        permissionProfile = inputSharingParameter.profile.fold(Profile.analyst.name)(_.value),
        taskRule = inputSharingParameter.taskRule.fold(defaultTaskRule)(_.value),
        observableRule = inputSharingParameter.observableRule.fold(defaultObservableRule)(_.value)
      )
  }

  implicit val profileOutput: Renderer.Aux[Profile with Entity, OutputProfile] = Renderer.toJson[Profile with Entity, OutputProfile](profile =>
    profile
      .asInstanceOf[Profile]
      .into[OutputProfile]
      .withFieldConst(_._id, profile._id.toString)
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

  implicit val dashboardOutput: Renderer.Aux[RichDashboard, OutputDashboard] = Renderer.toJson[RichDashboard, OutputDashboard](dashboard =>
    dashboard
      .into[OutputDashboard]
      .withFieldConst(_._id, dashboard._id.toString)
      .withFieldComputed(_.status, d => if (d.organisationShares.nonEmpty) "Shared" else "Private")
      .withFieldConst(_._type, "Dashboard")
      .withFieldConst(_._updatedAt, dashboard._updatedAt)
      .withFieldConst(_._updatedBy, dashboard._updatedBy)
      .withFieldConst(_._createdAt, dashboard._createdAt)
      .withFieldConst(_._createdBy, dashboard._createdBy)
      .withFieldComputed(_.definition, _.definition.toString)
      .enableMethodAccessors
      .transform
  )

  implicit val attachmentOutput: Renderer.Aux[Attachment with Entity, OutputAttachment] = Renderer.toJson[Attachment with Entity, OutputAttachment](
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
        .withFieldComputed(_.tlp, _.tlp.fold(2)(_.value))
        .withFieldComputed(_.tags, _.tags.toSeq.map(_.value))
        .withFieldConst(_.data, None)
        .transform
  }

  implicit class RichObservableOps(observable: RichObservable) {
    def toInputObservable(attachmentAsFFile: Boolean): InputObservable =
      InputObservable(
        dataType = String32("dataType", observable.dataType),
        data = observable.data.map(MultiLineString512("data", _)).toSeq,
        message = observable.message.map(Description("message", _)),
        startDate = None,
        attachment = observable
          .attachment
          .map { attachment =>
            if (attachmentAsFFile) Left(FFile(attachment.name, Paths.get(attachment.name), attachment.contentType))
            else
              Right(
                InputAttachment(
                  name = String128("attachement.name", attachment.name),
                  contentType = String128("attachement.contentType", attachment.contentType),
                  id = String128("attachement.id", attachment.attachmentId)
                )
              )
          }
          .toSeq,
        tlp = Some(Tlp(observable.tlp)),
        tags = observable.tags.map(String128("tags", _)).toSet,
        ioc = Some(observable.ioc),
        sighted = Some(observable.sighted),
        ignoreSimilarity = observable.ignoreSimilarity
      )
  }

  implicit val observableOutput: Renderer.Aux[RichObservable, OutputObservable] = Renderer.toJson[RichObservable, OutputObservable](richObservable =>
    richObservable
      .into[OutputObservable]
      .withFieldConst(_._type, "Observable")
      .withFieldComputed(_._id, _.observable._id.toString)
      .withFieldComputed(_._updatedAt, _.observable._updatedAt)
      .withFieldComputed(_._updatedBy, _.observable._updatedBy)
      .withFieldComputed(_._createdAt, _.observable._createdAt)
      .withFieldComputed(_._createdBy, _.observable._createdBy)
      .withFieldComputed(_.startDate, _.observable._createdAt)
      .withFieldComputed(_.tags, _.tags.toSet)
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
      .enableMethodAccessors
      .transform
  )

  implicit val observableWithExtraData: Renderer.Aux[(RichObservable, JsObject), OutputObservable] =
    Renderer.toJson[(RichObservable, JsObject), OutputObservable] {
      case (richObservable, extraData) =>
        richObservable
          .into[OutputObservable]
          .withFieldConst(_._type, "Observable")
          .withFieldComputed(_._id, _._id.toString)
          .withFieldComputed(_.startDate, _.observable._createdAt)
          .withFieldComputed(_.tags, _.tags.toSet)
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
          .enableMethodAccessors
          .transform
    }

  implicit val logOutput: Renderer.Aux[RichLog, OutputLog] = Renderer.toJson[RichLog, OutputLog](richLog =>
    richLog
      .into[OutputLog]
      .withFieldConst(_._type, "Log")
      .withFieldComputed(_._id, _._id.toString)
      .withFieldRenamed(_._createdAt, _.date)
      .withFieldComputed(_.attachment, _.attachments.headOption.map(_.toOutput))
      .withFieldRenamed(_._createdBy, _.owner)
      .withFieldConst(_.extraData, JsObject.empty)
      .enableMethodAccessors
      .transform
  )

  implicit val logWithStatsOutput: Renderer.Aux[(RichLog, JsObject), OutputLog] =
    Renderer.toJson[(RichLog, JsObject), OutputLog] { logWithExtraData =>
      logWithExtraData
        ._1
        .into[OutputLog]
        .withFieldConst(_._type, "Log")
        .withFieldComputed(_._id, _._id.toString)
        .withFieldRenamed(_._createdAt, _.date)
        .withFieldComputed(_.attachment, _.attachments.headOption.map(_.toOutput))
        .withFieldRenamed(_._createdBy, _.owner)
        .withFieldConst(_.extraData, logWithExtraData._2)
        .enableMethodAccessors
        .transform
    }

  implicit class InputLogOps(inputLog: InputLog) {

    def toLog: Log =
      inputLog
        .into[Log]
        .withFieldConst(_.date, new Date)
        .transform
  }

  implicit val observableTypeOutput: Renderer.Aux[ObservableType with Entity, OutputObservableType] =
    Renderer.toJson[ObservableType with Entity, OutputObservableType](observableType =>
      observableType
        .asInstanceOf[ObservableType]
        .into[OutputObservableType]
        .withFieldConst(_._id, observableType._id.toString)
        .withFieldConst(_._updatedAt, observableType._updatedAt)
        .withFieldConst(_._updatedBy, observableType._updatedBy)
        .withFieldConst(_._createdAt, observableType._createdAt)
        .withFieldConst(_._createdBy, observableType._createdBy)
        .withFieldConst(_._type, "ObservableType")
        .transform
    )

  implicit class InputObservableTypeOps(inputObservableType: InputObservableType) {
    def toObservableType: ObservableType =
      inputObservableType
        .into[ObservableType]
        .withFieldComputed(_.isAttachment, _.isAttachment.getOrElse(false))
        .transform
  }

  implicit class InputPatternOps(inputPattern: InputPattern) {
    def toPattern: Pattern =
      inputPattern
        .into[Pattern]
        .withFieldRenamed(_.external_id, _.patternId)
        .withFieldComputed(_.tactics, _.kill_chain_phases.map(_.phase_name).toSet)
        .withFieldRenamed(_.`type`, _.patternType)
        .withFieldRenamed(_.capec_id, _.capecId)
        .withFieldRenamed(_.capec_url, _.capecUrl)
        .withFieldRenamed(_.x_mitre_data_sources, _.dataSources)
        .withFieldRenamed(_.x_mitre_defense_bypassed, _.defenseBypassed)
        .withFieldRenamed(_.x_mitre_detection, _.detection)
        .withFieldRenamed(_.x_mitre_permissions_required, _.permissionsRequired)
        .withFieldRenamed(_.x_mitre_platforms, _.platforms)
        .withFieldRenamed(_.x_mitre_remote_support, _.remoteSupport)
        .withFieldRenamed(_.x_mitre_system_requirements, _.systemRequirements)
        .withFieldRenamed(_.x_mitre_version, _.revision)
        .transform
  }

  implicit class PatterOps(pattern: Pattern) {
    def toInputPattern: InputPattern =
      pattern
        .into[InputPattern]
        .withFieldRenamed(_.patternId, _.external_id)
        .withFieldComputed(_.kill_chain_phases, _.tactics.map(t => InputKillChainPhase("", t)).toSeq)
        .withFieldRenamed(_.patternType, _.`type`)
        .withFieldRenamed(_.capecId, _.capec_id)
        .withFieldRenamed(_.capecUrl, _.capec_url)
        .withFieldRenamed(_.dataSources, _.x_mitre_data_sources)
        .withFieldRenamed(_.defenseBypassed, _.x_mitre_defense_bypassed)
        .withFieldRenamed(_.detection, _.x_mitre_detection)
        .withFieldRenamed(_.permissionsRequired, _.x_mitre_permissions_required)
        .withFieldRenamed(_.platforms, _.x_mitre_platforms)
        .withFieldRenamed(_.remoteSupport, _.x_mitre_remote_support)
        .withFieldRenamed(_.systemRequirements, _.x_mitre_system_requirements)
        .withFieldRenamed(_.revision, _.x_mitre_version)
        .withFieldComputed(_.x_mitre_is_subtechnique, _.patternId.contains("."))
        .transform
  }

  implicit val patternOutput: Renderer.Aux[RichPattern, OutputPattern] =
    Renderer.toJson[RichPattern, OutputPattern](
      _.into[OutputPattern]
        .withFieldComputed(_._id, _._id.toString)
        .withFieldConst(_._type, "Pattern")
        .withFieldConst(_.extraData, JsObject.empty)
        .enableMethodAccessors
        .transform
    )

  implicit val patternWithStatsOutput: Renderer.Aux[(RichPattern, JsObject), OutputPattern] =
    Renderer.toJson[(RichPattern, JsObject), OutputPattern] { patternWithExtraData =>
      patternWithExtraData
        ._1
        .into[OutputPattern]
        .withFieldComputed(_._id, _._id.toString)
        .withFieldConst(_._type, "Pattern")
        .withFieldConst(_.extraData, patternWithExtraData._2)
        .enableMethodAccessors
        .transform
    }

  implicit class InputProcedureOps(inputProcedure: InputProcedure) {
    def toProcedure: Procedure =
      inputProcedure
        .into[Procedure]
        .transform
  }

  implicit class RichProcedureOps(richProcedure: RichProcedure) {
    def toInputProcedure: InputProcedure =
      InputProcedure(
        description = richProcedure.procedure.description.map(Description("description", _)),
        occurDate = richProcedure.occurDate,
        tactic = String32("tactic", richProcedure.procedure.tactic),
        caseId = String128("caseId", "caseId"), // don't export caseId
        patternId = String128("patternId", richProcedure.pattern.patternId)
      )
  }

  implicit val richProcedureRenderer: Renderer.Aux[RichProcedure, OutputProcedure] =
    Renderer.toJson[RichProcedure, OutputProcedure](
      _.into[OutputProcedure]
        .withFieldComputed(_._id, _._id.toString)
        .withFieldComputed(_.patternId, _.pattern.patternId)
        .withFieldConst(_.extraData, JsObject.empty)
        .enableMethodAccessors
        .transform
    )

  implicit val richProcedureWithStatsRenderer: Renderer.Aux[(RichProcedure, JsObject), OutputProcedure] =
    Renderer.toJson[(RichProcedure, JsObject), OutputProcedure] { procedureWithExtraData =>
      procedureWithExtraData
        ._1
        .into[OutputProcedure]
        .withFieldComputed(_._id, _._id.toString)
        .withFieldComputed(_.patternId, _.pattern.patternId)
        .withFieldConst(_.extraData, procedureWithExtraData._2)
        .enableMethodAccessors
        .transform
    }
}
