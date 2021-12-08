package org.thp.thehive.services

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter.ClassicSchedulerOps
import akka.actor.typed.{Scheduler, ActorRef => TypedActorRef}
import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.softwaremill.tagging.@@
import org.apache.tinkerpop.gremlin.process.traversal.{Order, P}
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.{FFile, FPathElem}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.Converter.Identity
import org.thp.scalligraph.traversal._
import org.thp.scalligraph.{BadRequestError, EntityId, EntityIdOrName, RichOptionTry, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.String64
import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.models._
import play.api.cache.SyncCacheApi
import play.api.libs.json.{JsNull, JsObject, JsValue, Json}

import java.lang.{Long => JLong}
import java.util.{Date, List => JList, Map => JMap}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class CaseSrv(
    tagSrv: TagSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val customFieldValueSrv: CustomFieldValueSrv,
    override val organisationSrv: OrganisationSrv,
    shareSrv: ShareSrv,
    taskSrv: TaskSrv,
    auditSrv: AuditSrv,
    resolutionStatusSrv: ResolutionStatusSrv,
    impactStatusSrv: ImpactStatusSrv,
    observableSrv: ObservableSrv,
    attachmentSrv: AttachmentSrv,
    userSrv: UserSrv,
    _alertSrv: => AlertSrv,
    integrityCheckActor: => ActorRef @@ IntegrityCheckTag,
    caseNumberActor: => TypedActorRef[CaseNumberActor.Request],
    cache: SyncCacheApi,
    implicit val ec: ExecutionContext,
    implicit val actorSystem: ActorSystem
) extends VertexSrv[Case]
    with ElementOps
    with TheHiveOps {
  lazy val alertSrv: AlertSrv = _alertSrv

  val caseTagSrv              = new EdgeSrv[CaseTag, Case, Tag]
  val caseImpactStatusSrv     = new EdgeSrv[CaseImpactStatus, Case, ImpactStatus]
  val caseResolutionStatusSrv = new EdgeSrv[CaseResolutionStatus, Case, ResolutionStatus]
  val caseUserSrv             = new EdgeSrv[CaseUser, Case, User]
  val caseCustomFieldValueSrv = new EdgeSrv[CaseCustomFieldValue, Case, CustomFieldValue]
  val caseCaseTemplateSrv     = new EdgeSrv[CaseCaseTemplate, Case, CaseTemplate]
  val caseProcedureSrv        = new EdgeSrv[CaseProcedure, Case, Procedure]
  val shareCaseSrv            = new EdgeSrv[ShareCase, Share, Case]
  val mergedFromSrv           = new EdgeSrv[MergedFrom, Case, Case]

  override def createEntity(e: Case)(implicit graph: Graph, authContext: AuthContext): Try[Case with Entity] =
    super.createEntity(e).map { `case` =>
      integrityCheckActor ! EntityAdded("Case")
      `case`
    }

  def create(
      `case`: Case,
      assignee: Option[User with Entity],
      customFields: Seq[InputCustomFieldValue],
      caseTemplate: Option[RichCaseTemplate],
      additionalTasks: Seq[Task],
      sharingParameters: Map[String, SharingParameter],
      taskRule: Option[String],
      observableRule: Option[String]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCase] = {
    val caseNumber = if (`case`.number == 0) nextCaseNumber else `case`.number
    val tagNames   = (`case`.tags ++ caseTemplate.fold[Seq[String]](Nil)(_.tags)).distinct
    for {
      currentOrganisation <- organisationSrv.current.getOrFail("Organisation")
      tags                <- tagNames.toTry(tagSrv.getOrCreate)
      createdCase <- createEntity(
        `case`.copy(
          number = caseNumber,
          assignee = assignee.map(_.login),
          organisationIds = Set(currentOrganisation._id),
          caseTemplate = caseTemplate.map(_.name),
          impactStatus = None,
          resolutionStatus = None,
          tags = tagNames,
          owningOrganisation = currentOrganisation._id
        )
      )

      ownerSharingProfile <- organisationSrv.current.ownerSharingProfile(taskRule, observableRule).getOrFail("Organisation")
      _                   <- assignee.map(u => caseUserSrv.create(CaseUser(), createdCase, u)).flip
      _                   <- shareSrv.shareCase(owner = true, createdCase, currentOrganisation, ownerSharingProfile)
      _                   <- caseTemplate.map(ct => caseCaseTemplateSrv.create(CaseCaseTemplate(), createdCase, ct.caseTemplate)).flip
      _                   <- caseTemplate.fold(additionalTasks)(_.tasks.map(_.task) ++ additionalTasks).toTry(task => createTask(createdCase, task))
      _                   <- tags.toTry(caseTagSrv.create(CaseTag(), createdCase, _))

      caseTemplateCf =
        caseTemplate
          .fold[Seq[RichCustomField]](Seq())(_.customFields)
          .map(cf => InputCustomFieldValue(String64("customField.name", cf.name), cf.jsValue, cf.order))
      cfs <- cleanCustomFields(caseTemplateCf, customFields).toTry(createCustomField(createdCase, _))

      _ = organisationSrv.current.sharingProfiles(sharingParameters).foreach {
        case (orgId, sharingProfile) =>
          organisationSrv
            .getOrFail(orgId)
            .flatMap(org => shareSrv.shareCase(owner = false, createdCase, org, sharingProfile))
            .recover { // don't prevent case creation if autoSharing fails
              case error =>
                logger.error(s"Unable to share the case #${createdCase.number} to organisation $orgId", error)
            }
      }

      richCase = RichCase(createdCase, cfs, authContext.permissions)
      _ <- auditSrv.`case`.create(createdCase, richCase.toJson)
    } yield richCase
  }

  def caseId(idOrName: EntityIdOrName)(implicit graph: Graph): EntityId =
    idOrName.fold(identity, oid => cache.getOrElseUpdate(s"case-$oid")(getByName(oid)._id.getOrFail("Case").get))

  private def cleanCustomFields(caseTemplateCf: Seq[InputCustomFieldValue], caseCf: Seq[InputCustomFieldValue]): Seq[InputCustomFieldValue] = {
    val uniqueFields = caseTemplateCf.filter {
      case InputCustomFieldValue(name, _, _) => !caseCf.exists(_.name == name)
    }
    (caseCf ++ uniqueFields)
      .sortBy(cf => (cf.order.isEmpty, cf.order))
      .zipWithIndex
      .map { case (InputCustomFieldValue(name, value, _), i) => InputCustomFieldValue(name, value, Some(i)) }
  }

  def nextCaseNumberAsync: Future[Int] = {
    implicit val timeout: Timeout     = Timeout(1.minute)
    implicit val scheduler: Scheduler = actorSystem.scheduler.toTyped
    caseNumberActor.ask[CaseNumberActor.Response](replyTo => CaseNumberActor.GetNextNumber(replyTo)).map {
      case CaseNumberActor.NextNumber(caseNumber) => caseNumber
    }
  }

  def nextCaseNumber: Int =
    Await.result(nextCaseNumberAsync, 1.minute)

  override def exists(e: Case)(implicit graph: Graph): Boolean = startTraversal.getByNumber(e.number).exists

  override def update(
      traversal: Traversal.V[Case],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Case], JsObject)] = {
    val closeCase = PropertyUpdater(FPathElem("closeCase"), "") { (vertex, _, _) =>
      get(vertex)
        .tasks
        .or(_.has(_.status, TaskStatus.Waiting), _.has(_.status, TaskStatus.InProgress))
        .toIterator
        .toTry {
          case task if task.status == TaskStatus.InProgress => taskSrv.updateStatus(task, TaskStatus.Completed)
          case task                                         => taskSrv.updateStatus(task, TaskStatus.Cancel)
        }
        .flatMap { _ =>
          vertex.setProperty[Option[Date]]("endDate", Some(new Date()))
          Success(Json.obj("endDate" -> System.currentTimeMillis()))
        }
    }

    val isCloseCase = propertyUpdaters.exists(p => p.path.matches(FPathElem("status")) && p.value == CaseStatus.Resolved)

    val newPropertyUpdaters = if (isCloseCase) closeCase +: propertyUpdaters else propertyUpdaters
    auditSrv.mergeAudits(super.update(traversal, newPropertyUpdaters)) {
      case (caseSteps, updatedFields) =>
        caseSteps
          .clone()
          .getOrFail("Case")
          .flatMap(auditSrv.`case`.update(_, updatedFields))
    }
  }

  def updateTags(`case`: Case with Entity, tags: Set[String])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[(Seq[Tag with Entity], Seq[Tag with Entity])] =
    for {
      tagsToAdd <- (tags -- `case`.tags).toTry(tagSrv.getOrCreate)
      tagsToRemove = get(`case`).tags.toSeq.filterNot(t => tags.contains(t.toString))
      _ <- tagsToAdd.toTry(caseTagSrv.create(CaseTag(), `case`, _))
      _ = if (tagsToRemove.nonEmpty) get(`case`).outE[CaseTag].filter(_.otherV().hasId(tagsToRemove.map(_._id): _*)).remove()
      _ <- get(`case`)
        .update(_.tags, tags.toSeq)
        .update(_._updatedAt, Some(new Date))
        .update(_._updatedBy, Some(authContext.userId))
        .getOrFail("Case")
      _ <- auditSrv.`case`.update(`case`, Json.obj("tags" -> tags))
    } yield (tagsToAdd, tagsToRemove)

  def addTags(`case`: Case with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    updateTags(`case`, tags ++ `case`.tags).map(_ => ())

  def createTask(`case`: Case with Entity, task: Task)(implicit graph: Graph, authContext: AuthContext): Try[RichTask] =
    for {
      assignee <- task.assignee.map(u => get(`case`).assignableUsers.getByName(u).getOrFail("User")).flip
      task     <- taskSrv.create(task.copy(relatedId = `case`._id, organisationIds = Set(organisationSrv.currentId)), assignee)
      _        <- shareSrv.addTaskToCase(task, `case`, organisationSrv.currentId)
    } yield task

  def createObservable(`case`: Case with Entity, observable: Observable, data: String)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      createdObservable <- observableSrv.create(observable.copy(organisationIds = Set(organisationSrv.currentId), relatedId = `case`._id), data)
      _                 <- shareSrv.addObservableToCase(createdObservable, `case`, organisationSrv.currentId)
    } yield createdObservable

  def createObservable(`case`: Case with Entity, observable: Observable, attachment: Attachment with Entity)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      createdObservable <- observableSrv.create(observable.copy(organisationIds = Set(organisationSrv.currentId), relatedId = `case`._id), attachment)
      _                 <- shareSrv.addObservableToCase(createdObservable, `case`, organisationSrv.currentId)
    } yield createdObservable

  def createObservable(`case`: Case with Entity, observable: Observable, file: FFile)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    attachmentSrv.create(file).flatMap(attachment => createObservable(`case`, observable, attachment))

  override def delete(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val details = Json.obj("number" -> `case`.number, "title" -> `case`.title)
    organisationSrv.get(authContext.organisation).getOrFail("Organisation").flatMap { organisation =>
      shareSrv
        .get(`case`, authContext.organisation)
        .getOrFail("Share")
        .flatMap {
          case share if share.owner =>
            get(`case`)
              .sideEffect(_.alert.update(_.caseId, EntityId.empty))
              .shares
              .toSeq
              .toTry(s => shareSrv.unshareCase(s._id))
              .map(_ => get(`case`).remove())
          case _ =>
            throw BadRequestError("Your organisation must be owner of the case")
          // shareSrv.unshareCase(share._id)
        }
        .map { _ =>
          auditSrv.`case`.delete(`case`, organisation, Some(details))
          ()
        }
    }
  }

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Case] =
    Try(startTraversal.getByNumber(name.toInt)).getOrElse(startTraversal.empty)

  def createCustomField(
      `case`: Case with Entity,
      customField: InputCustomFieldValue
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    customFieldSrv
      .getOrFail(EntityIdOrName(customField.name.value))
      .flatMap(cf => createCustomField(`case`, cf, customField.value, customField.order))

  def createCustomField(
      `case`: Case with Entity,
      customField: RichCustomField
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    createCustomField(`case`, customField.customField, customField.jsValue, customField.order)

  def createCustomField(
      `case`: Case with Entity,
      customField: CustomField with Entity,
      customFieldValue: JsValue,
      order: Option[Int]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    for {
      cfv <- customFieldValueSrv.createCustomField(`case`, customField, customFieldValue, order)
      _   <- caseCustomFieldValueSrv.create(CaseCustomFieldValue(), `case`, cfv)
    } yield RichCustomField(customField, cfv)

  def updateOrCreateCustomField(
      `case`: Case with Entity,
      customField: CustomField with Entity,
      value: JsValue,
      order: Option[Int]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    get(`case`)
      .customFieldValue
      .has(_.name, customField.name)
      .headOption
      .fold(createCustomField(`case`, customField, value, order)) { cfv =>
        customFieldValueSrv
          .updateValue(cfv, customField.`type`, value, order)
          .map(RichCustomField(customField, _))
      }

  def updateCustomField(caseId: EntityIdOrName, customFieldValue: EntityId, value: JsValue)(implicit graph: Graph): Try[CustomFieldValue] =
    customFieldValueSrv
      .getByIds(customFieldValue)
      .filter(_.`case`.get(caseId))
      .getOrFail("CustomField")
      .flatMap(cfv => customFieldValueSrv.updateValue(cfv, value, None))

  def setImpactStatus(
      `case`: Case with Entity,
      impactStatus: String
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    impactStatusSrv.getOrFail(EntityIdOrName(impactStatus)).flatMap(setImpactStatus(`case`, _))

  def setImpactStatus(
      `case`: Case with Entity,
      impactStatus: ImpactStatus with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`)
      .update(_.impactStatus, Some(impactStatus.value))
      .update(_._updatedAt, Some(new Date))
      .update(_._updatedBy, Some(authContext.userId))
      .outE[CaseImpactStatus]
      .remove()
    caseImpactStatusSrv.create(CaseImpactStatus(), `case`, impactStatus)
    auditSrv.`case`.update(`case`, Json.obj("impactStatus" -> impactStatus.value))
  }

  def unsetImpactStatus(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`)
      .update(_.impactStatus, None)
      .update(_._updatedAt, Some(new Date))
      .update(_._updatedBy, Some(authContext.userId))
      .outE[CaseImpactStatus]
      .remove()
    auditSrv.`case`.update(`case`, Json.obj("impactStatus" -> JsNull))
  }

  def setResolutionStatus(
      `case`: Case with Entity,
      resolutionStatus: String
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    resolutionStatusSrv.getOrFail(EntityIdOrName(resolutionStatus)).flatMap(setResolutionStatus(`case`, _))

  def setResolutionStatus(
      `case`: Case with Entity,
      resolutionStatus: ResolutionStatus with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`)
      .update(_.resolutionStatus, Some(resolutionStatus.value))
      .update(_._updatedAt, Some(new Date))
      .update(_._updatedBy, Some(authContext.userId))
      .outE[CaseResolutionStatus]
      .remove()
    caseResolutionStatusSrv.create(CaseResolutionStatus(), `case`, resolutionStatus)
    auditSrv.`case`.update(`case`, Json.obj("resolutionStatus" -> resolutionStatus.value))
  }

  def unsetResolutionStatus(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`)
      .update(_.resolutionStatus, None)
      .update(_._updatedAt, Some(new Date))
      .update(_._updatedBy, Some(authContext.userId))
      .outE[CaseResolutionStatus]
      .remove()
    auditSrv.`case`.update(`case`, Json.obj("resolutionStatus" -> JsNull))
  }

  def assign(`case`: Case with Entity, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`)
      .update(_.assignee, Some(user.login))
      .update(_._updatedAt, Some(new Date))
      .update(_._updatedBy, Some(authContext.userId))
      .outE[CaseUser]
      .remove()
    caseUserSrv.create(CaseUser(), `case`, user)
    auditSrv.`case`.update(`case`, Json.obj("owner" -> user.login))
  }

  def unassign(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`)
      .update(_.assignee, None)
      .update(_._updatedAt, Some(new Date))
      .update(_._updatedBy, Some(authContext.userId))
      .outE[CaseUser]
      .remove()
    auditSrv.`case`.update(`case`, Json.obj("owner" -> JsNull))
  }

  def merge(cases: Seq[Case with Entity])(implicit graph: Graph, authContext: AuthContext): Try[RichCase] =
    if (cases.size > 1 && canMerge(cases))
      auditSrv.mergeAudits {
        val mergedCase = Case(
          cases.map(_.title).mkString(" / "),
          cases.map(_.description).mkString("\n\n"),
          cases.map(_.severity).max,
          cases.map(_.startDate).min,
          None,
          cases.exists(_.flag),
          cases.map(_.tlp).max,
          cases.map(_.pap).max,
          CaseStatus.Open,
          cases.map(_.summary).fold(None)((s1, s2) => (s1 ++ s2).reduceOption(_ + "\n\n" + _)),
          cases.flatMap(_.tags).distinct
        )

        val sharingProfiles: Seq[(Organisation with Entity, SharingProfile)] = get(cases.head)
          .shares
          .sharingProfiles
          .toSeq

        for {
          user <- userSrv.current.getOrFail("User")
          richCase <- create(
            mergedCase,
            Some(user),
            customFields = Seq(),
            caseTemplate = None,
            additionalTasks = Seq(),
            sharingParameters = Map.empty,
            taskRule = None,
            observableRule = None
          ) // FIXME Add customfields and case template
          // Share case with all organisations except the one who created the merged case
//        _ <-
//          sharingProfiles
          //            .filterNot(_._1._id == currentOrgaId)
          //            .toTry(sharingProfile => shareSrv.shareCase(owner = false, richCase.`case`, sharingProfile._1, sharingProfile._2))
          _ <- cases.toTry { c =>
            for {
              _ <- shareMergedCaseTasks(sharingProfiles.map(_._1), c, richCase.`case`)
              _ <- shareMergedCaseObservables(sharingProfiles.map(_._1), c, richCase.`case`)
              _ <-
                get(c)
                  .alert
                  .update(_.caseId, richCase._id)
                  .toSeq
                  .toTry(alertSrv.alertCaseSrv.create(AlertCase(), _, richCase.`case`))
              _ <-
                get(c)
                  .procedure
                  .toSeq
                  .toTry(caseProcedureSrv.create(CaseProcedure(), richCase.`case`, _))
              _ <-
                get(c)
                  .richCustomFields
                  .toSeq
                  .toTry(createCustomField(richCase.`case`, _))
            } yield Success(())
          }
          _ <- cases.toTry(super.delete(_))
        } yield richCase
      }(mergedCase =>
        auditSrv
          .`case`
          .merge(mergedCase.`case`, Some(Json.obj("cases" -> cases.map(c => Json.obj("_id" -> c._id, "number" -> c.number, "title" -> c.title)))))
      )
    else
      Failure(BadRequestError("To be able to merge, cases must have same organisation / profile pair and user must be org-admin"))

  private def canMerge(cases: Seq[Case with Entity])(implicit graph: Graph, authContext: AuthContext): Boolean = {
    val allOrgProfiles = getByIds(cases.map(_._id): _*)
      .flatMap(_.shares.project(_.by(_.profile.value(_.name)).by(_.organisation._id)).fold)
      .toSeq
      .map(_.toSet)
      .distinct

    // All cases must have the same organisation / profile pair &&
    // case organisation must match current organisation and be of org-admin profile
    allOrgProfiles.size == 1 && allOrgProfiles
      .head
      .exists {
        case (profile, orgId) => orgId == organisationSrv.currentId && profile == Profile.orgAdmin.name
      }
  }

  private def shareMergedCaseTasks(orgs: Seq[Organisation with Entity], fromCase: Case with Entity, mergedCase: Case with Entity)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    orgs
      .toTry { org =>
        get(fromCase)
          .share(org._id)
          .tasks
          .update(_.relatedId, mergedCase._id)
          .richTask
          .toSeq
          .toTry(shareSrv.addTaskToCase(_, mergedCase, org._id))
      }
      .map(_ => ())

  private def shareMergedCaseObservables(orgs: Seq[Organisation with Entity], fromCase: Case with Entity, mergedCase: Case with Entity)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    orgs
      .toTry { org =>
        get(fromCase)
          .share(org._id)
          .observables
          .update(_.relatedId, mergedCase._id)
          .richObservable
          .toSeq
          .toTry(shareSrv.addObservableToCase(_, mergedCase, org._id))
      }
      .map(_ => ())
}

trait CaseOpsNoDeps { ops: TheHiveOpsNoDeps =>

  implicit class CaseOpsNoDepsDefs(traversal: Traversal.V[Case])
      extends EntityWithCustomFieldOpsNoDepsDefs[Case, CaseCustomFieldValue](traversal, ops) {

    def resolutionStatus: Traversal.V[ResolutionStatus] = traversal.out[CaseResolutionStatus].v[ResolutionStatus]

    def get(idOrName: EntityIdOrName): Traversal.V[Case] =
      idOrName.fold(traversal.getByIds(_), n => getByNumber(n.toInt))

    def getByNumber(caseNumber: Int): Traversal.V[Case] = traversal.has(_.number, caseNumber)

    def assignee: Traversal.V[User] = traversal.out[CaseUser].v[User]

    def assignedTo(userLogin: String*): Traversal.V[Case] =
      if (userLogin.isEmpty) traversal.empty
      else if (userLogin.size == 1) traversal.has(_.assignee, userLogin.head)
      else traversal.has(_.assignee, P.within(userLogin: _*))

    def caseTemplate: Traversal.V[CaseTemplate] = traversal.out[CaseCaseTemplate].v[CaseTemplate]

    def can(permission: Permission)(implicit authContext: AuthContext): Traversal.V[Case] =
      if (authContext.permissions.contains(permission))
        traversal.filter(_.share.profile.has(_.permissions, permission))
      else
        traversal.empty

    def getLast: Traversal.V[Case] =
      traversal.sort(_.by("number", Order.desc)).limit(1)

    def richCaseWithCustomRenderer[D, G, C <: Converter[D, G]](
        entityRenderer: Traversal.V[Case] => Traversal[D, G, C]
    )(implicit authContext: AuthContext): Traversal[(RichCase, D), JMap[String, Any], Converter[(RichCase, D), JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.richCustomFields.fold)
            .by(entityRenderer)
            .by(_.userPermissions)
        )
        .domainMap {
          case (caze, customFields, renderedEntity, userPermissions) =>
            RichCase(
              caze,
              customFields,
              userPermissions
            ) -> renderedEntity
        }

    def share(implicit authContext: AuthContext): Traversal.V[Share] = share(authContext.organisation)

    def share(organisation: EntityIdOrName): Traversal.V[Share] =
      shares.filter(_.organisation.get(organisation)).v[Share]

    def shares: Traversal.V[Share] = traversal.in[ShareCase].v[Share]

    def organisations: Traversal.V[Organisation] = traversal.in[ShareCase].in[OrganisationShare].v[Organisation]

    def organisations(permission: Permission): Traversal.V[Organisation] =
      shares.filter(_.profile.has(_.permissions, permission)).organisation

    def userPermissions(implicit authContext: AuthContext): Traversal[Set[Permission], JList[String], Converter[Set[Permission], JList[String]]] =
      traversal
        .share(authContext.organisation)
        .profile
        .value(_.permissions)
        .fold
        .domainMap(_.toSet & authContext.permissions)

    def origin: Traversal.V[Organisation] = shares.has(_.owner, true).organisation

//    def audits(organisationSrv: OrganisationSrv)(implicit authContext: AuthContext): Traversal.V[Audit] =
//      traversal
//        .unionFlat(_.visible(organisationSrv), _.observables(organisationIdOrName), _.tasks(organisationIdOrName), _.share(organisationIdOrName))
//        .in[AuditContext]
//        .v[Audit]

    def linkedCases(implicit
        authContext: AuthContext
    ): Traversal[(RichCase, Seq[RichObservable]), JMap[String, Any], Converter[(RichCase, Seq[RichObservable]), JMap[String, Any]]] = {
      val originCaseLabel = StepLabel.v[Case]
      val observableLabel = StepLabel.v[Observable] // TODO add similarity on attachment
      traversal
        .as(originCaseLabel)
        .observables
        .hasNot(_.ignoreSimilarity, true)
        .as(observableLabel)
        .data
        .observables
        .hasNot(_.ignoreSimilarity, true)
        .shares
        .filter(_.organisation.current)
        .`case`
        .where(P.neq(originCaseLabel.name))
        .group(_.by, _.by(_.select(observableLabel).richObservable.fold))
        .unfold
        .project(_.by(_.selectKeys.richCase).by(_.selectValues))
    }

    def isShared: Traversal[Boolean, Boolean, Identity[Boolean]] =
      traversal.choose(_.inE[ShareCase].count.is(P.gt(1)), true, false)

    def richCase(implicit authContext: AuthContext): Traversal[RichCase, JMap[String, Any], Converter[RichCase, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.richCustomFields.fold)
            .by(_.userPermissions)
        )
        .domainMap {
          case (caze, customFields, userPermissions) =>
            RichCase(
              caze,
              customFields,
              userPermissions
            )
        }

    def user: Traversal.V[User] = traversal.out[CaseUser].v[User]

    def richCaseWithoutPerms: Traversal[RichCase, JMap[String, Any], Converter[RichCase, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.richCustomFields.fold)
        )
        .domainMap {
          case (caze, customFields) =>
            RichCase(
              caze,
              customFields,
              Set.empty
            )
        }

    def tags: Traversal.V[Tag] = traversal.out[CaseTag].v[Tag]

    def impactStatus: Traversal.V[ImpactStatus] = traversal.out[CaseImpactStatus].v[ImpactStatus]

    def tasks(implicit authContext: AuthContext): Traversal.V[Task] = tasks(authContext.organisation)

    def tasks(organisationIdOrName: EntityIdOrName): Traversal.V[Task] =
      share(organisationIdOrName).tasks

    def observables(implicit authContext: AuthContext): Traversal.V[Observable] = observables(authContext.organisation)

    def observables(organisationIdOrName: EntityIdOrName): Traversal.V[Observable] =
      share(organisationIdOrName).observables

    def assignableUsers(implicit authContext: AuthContext): Traversal.V[User] =
      organisations(Permissions.manageCase)
        .visible
        .users(Permissions.manageCase)
        .dedup

    def alert: Traversal.V[Alert] = traversal.in[AlertCase].v[Alert]

    def procedure: Traversal.V[Procedure] = traversal.out[CaseProcedure].v[Procedure]

    def isActionRequired(implicit authContext: AuthContext): Traversal[Boolean, Boolean, Converter.Identity[Boolean]] =
      traversal.choose(_.share(authContext).outE[ShareTask].has(_.actionRequired, true), true, false)

    def handlingDuration: Traversal[Long, Long, IdentityConverter[Long]] =
      traversal.coalesceIdent(
        _.has(_.endDate)
          .sack(
            (_: JLong, importDate: JLong) => importDate,
            _.by(_.value(_.endDate).graphMap[Long, JLong, Converter[Long, JLong]](_.getTime, Converter.long))
          )
          .sack((_: Long) - (_: JLong), _.by(_._createdAt.graphMap[Long, JLong, Converter[Long, JLong]](_.getTime, Converter.long)))
          .sack[Long],
        _.constant(0L)
      )
  }
}

trait CaseOps { ops: TheHiveOps =>

  implicit class CaseOpsDefs(traversal: Traversal.V[Case]) extends EntityWithCustomFieldOpsDefs[Case, CaseCustomFieldValue](traversal, ops) {

    def visible(implicit authContext: AuthContext): Traversal.V[Case] =
      traversal.has(_.organisationIds, organisationSrv.currentId(traversal.graph, authContext))
  }
}

class CaseIntegrityCheckOps(
    val db: Database,
    val service: CaseSrv,
    userSrv: UserSrv,
    caseTemplateSrv: CaseTemplateSrv,
    organisationSrv: OrganisationSrv
) extends IntegrityCheckOps[Case]
    with TheHiveOpsNoDeps {

  override def resolve(entities: Seq[Case with Entity])(implicit graph: Graph): Try[Unit] = {
    EntitySelector
      .firstCreatedEntity(entities)
      .foreach(
        _._2
          .flatMap(service.get(_).setConverter[Vertex, Converter.Identity[Vertex]](Converter.identity).headOption)
          .foreach { vertex =>
            val nextNumber = service.nextCaseNumber
            UMapping.int.setProperty(vertex, "number", nextNumber)
          }
      )
    Success(())
  }

  override def globalCheck(): Map[String, Int] =
    db.tryTransaction { implicit graph =>
      Try {
        service
          .startTraversal
          .project(
            _.by
              .by(_.organisations._id.fold)
              .by(_.assignee.value(_.login).fold)
              .by(_.caseTemplate.value(_.name).fold)
              .by(_.origin._id.fold)
          )
          .toIterator
          .map {
            case (case0, organisationIds, assigneeIds, caseTemplateNames, owningOrganisationIds) =>
              val fixOwningOrg: LinkRemover =
                (caseId, orgId) => service.get(caseId).shares.filter(_.organisation.get(orgId._id)).update(_.owner, false).iterate()

              val assigneeStats = singleOptionLink[User, String]("assignee", userSrv.getByName(_).head, _.login)(_.outEdge[CaseUser])
                .check(case0, case0.assignee, assigneeIds)
              val orgStats = multiIdLink[Organisation]("organisationIds", organisationSrv)(_.remove) // FIXME => Seq => Set
                .check(case0, case0.organisationIds, organisationIds)
              val templateStats =
                singleOptionLink[CaseTemplate, String]("caseTemplate", caseTemplateSrv.getByName(_).head, _.name)(_.outEdge[CaseCaseTemplate])
                  .check(case0, case0.caseTemplate, caseTemplateNames)
              val owningOrgStats = singleIdLink[Organisation]("owningOrganisation", organisationSrv)(_ => fixOwningOrg, _.remove)
                .check(case0, case0.owningOrganisation, owningOrganisationIds)

              assigneeStats <+> orgStats <+> templateStats <+> owningOrgStats
          }
          .reduceOption(_ <+> _)
          .getOrElse(Map.empty)
      }
    }.getOrElse(Map("globalFailure" -> 1))
}
