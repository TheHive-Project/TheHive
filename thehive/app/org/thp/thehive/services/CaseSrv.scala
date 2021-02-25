package org.thp.thehive.services

import akka.actor.ActorRef
import org.apache.tinkerpop.gremlin.process.traversal.{Order, P}
import org.apache.tinkerpop.gremlin.structure.{Graph, Vertex}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FPathElem
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, IdentityConverter, StepLabel, Traversal}
import org.thp.scalligraph.{CreateError, EntityIdOrName, EntityName, RichOptionTry, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CustomFieldOps._
import org.thp.thehive.services.DataOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.TaskOps._
import play.api.libs.json.{JsNull, JsObject, Json}

import java.lang.{Long => JLong}
import java.util.{Map => JMap}
import javax.inject.{Inject, Named, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton
class CaseSrv @Inject() (
    tagSrv: TagSrv,
    customFieldSrv: CustomFieldSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv,
    profileSrv: ProfileSrv,
    shareSrv: ShareSrv,
    taskSrv: TaskSrv,
    auditSrv: AuditSrv,
    resolutionStatusSrv: ResolutionStatusSrv,
    impactStatusSrv: ImpactStatusSrv,
    @Named("integrity-check-actor") integrityCheckActor: ActorRef
)(implicit @Named("with-thehive-schema") db: Database)
    extends VertexSrv[Case] {

  val caseTagSrv              = new EdgeSrv[CaseTag, Case, Tag]
  val caseImpactStatusSrv     = new EdgeSrv[CaseImpactStatus, Case, ImpactStatus]
  val caseResolutionStatusSrv = new EdgeSrv[CaseResolutionStatus, Case, ResolutionStatus]
  val caseUserSrv             = new EdgeSrv[CaseUser, Case, User]
  val caseCustomFieldSrv      = new EdgeSrv[CaseCustomField, Case, CustomField]
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
      user: Option[User with Entity],
      organisation: Organisation with Entity,
      tags: Set[Tag with Entity],
      customFields: Seq[InputCustomFieldValue],
      caseTemplate: Option[RichCaseTemplate],
      additionalTasks: Seq[(Task, Option[User with Entity])]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCase] =
    for {
      createdCase <- createEntity(if (`case`.number == 0) `case`.copy(number = nextCaseNumber) else `case`)
      assignee    <- user.fold(userSrv.current.getOrFail("User"))(Success(_))
      _           <- caseUserSrv.create(CaseUser(), createdCase, assignee)
      _           <- shareSrv.shareCase(owner = true, createdCase, organisation, profileSrv.orgAdmin)
      _           <- caseTemplate.map(ct => caseCaseTemplateSrv.create(CaseCaseTemplate(), createdCase, ct.caseTemplate)).flip

      createdTasks <- caseTemplate.fold(additionalTasks)(_.tasks.map(t => t.task -> t.assignee)).toTry {
        case (task, owner) => taskSrv.create(task, owner)
      }
      _ <- createdTasks.toTry(t => shareSrv.shareTask(t, createdCase, organisation))

      caseTemplateCf =
        caseTemplate
          .fold[Seq[RichCustomField]](Seq())(_.customFields)
          .map(cf => InputCustomFieldValue(cf.name, cf.value, cf.order))
      cfs <- cleanCustomFields(caseTemplateCf, customFields).toTry {
        case InputCustomFieldValue(name, value, order) => createCustomField(createdCase, EntityIdOrName(name), value, order)
      }

      caseTemplateTags = caseTemplate.fold[Seq[Tag with Entity]](Nil)(_.tags)
      allTags          = tags ++ caseTemplateTags
      _ <- allTags.toTry(t => caseTagSrv.create(CaseTag(), createdCase, t))

      richCase = RichCase(createdCase, allTags.toSeq, None, None, Some(assignee.login), cfs, authContext.permissions)
      _ <- auditSrv.`case`.create(createdCase, richCase.toJson)
    } yield richCase

  private def cleanCustomFields(caseTemplateCf: Seq[InputCustomFieldValue], caseCf: Seq[InputCustomFieldValue]): Seq[InputCustomFieldValue] = {
    val uniqueFields = caseTemplateCf.filter {
      case InputCustomFieldValue(name, _, _) => !caseCf.exists(_.name == name)
    }
    (caseCf ++ uniqueFields)
      .sortBy(cf => (cf.order.isEmpty, cf.order))
      .zipWithIndex
      .map { case (InputCustomFieldValue(name, value, _), i) => InputCustomFieldValue(name, value, Some(i)) }
  }

  def nextCaseNumber(implicit graph: Graph): Int = startTraversal.getLast.headOption.fold(0)(_.number) + 1

  override def exists(e: Case)(implicit graph: Graph): Boolean = startTraversal.getByNumber(e.number).exists

  override def update(
      traversal: Traversal.V[Case],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Case], JsObject)] = {
    val closeCase = PropertyUpdater(FPathElem("closeCase"), "") { (vertex, _, _, _) =>
      get(vertex)
        .tasks
        .or(_.has(_.status, TaskStatus.Waiting), _.has(_.status, TaskStatus.InProgress))
        .toIterator
        .toTry {
          case task if task.status == TaskStatus.InProgress => taskSrv.updateStatus(task, null, TaskStatus.Completed)
          case task                                         => taskSrv.updateStatus(task, null, TaskStatus.Cancel)
        }
        .flatMap { _ =>
          vertex.property("endDate", System.currentTimeMillis())
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

  def updateTagNames(`case`: Case with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    tags.toTry(tagSrv.getOrCreate).flatMap(t => updateTags(`case`, t.toSet))

  private def updateTags(`case`: Case with Entity, tags: Set[Tag with Entity])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val (tagsToAdd, tagsToRemove) = get(`case`)
      .tags
      .toIterator
      .foldLeft((tags, Set.empty[Tag with Entity])) {
        case ((toAdd, toRemove), t) if toAdd.contains(t) => (toAdd - t, toRemove)
        case ((toAdd, toRemove), t)                      => (toAdd, toRemove + t)
      }
    for {
      _ <- tagsToAdd.toTry(caseTagSrv.create(CaseTag(), `case`, _))
      _ = get(`case`).removeTags(tagsToRemove)
      _ <- auditSrv.`case`.update(`case`, Json.obj("tags" -> tags.map(_.toString)))
    } yield ()
  }

  def addTags(`case`: Case with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val currentTags = get(`case`)
      .tags
      .toSeq
      .map(_.toString)
      .toSet
    for {
      createdTags <- (tags -- currentTags).toTry(tagSrv.getOrCreate)
      _           <- createdTags.toTry(caseTagSrv.create(CaseTag(), `case`, _))
      _           <- auditSrv.`case`.update(`case`, Json.obj("tags" -> (currentTags ++ tags)))
    } yield ()
  }

  def addObservable(`case`: Case with Entity, richObservable: RichObservable)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
    val alreadyExistInThatCase = richObservable
      .data
      .fold(false)(data => get(`case`).observables.data.has(_.data, data.data).exists)

    if (alreadyExistInThatCase)
      Failure(CreateError("Observable already exists"))
    else
      for {
        organisation <- organisationSrv.getOrFail(authContext.organisation)
        _            <- shareSrv.shareObservable(richObservable, `case`, organisation)
      } yield ()
  }

  def remove(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val details = Json.obj("number" -> `case`.number, "title" -> `case`.title)
    for {
      organisation <- organisationSrv.getOrFail(authContext.organisation)
      _            <- auditSrv.`case`.delete(`case`, organisation, Some(details))
    } yield {
      get(`case`).share.remove()
      get(`case`).remove()
    }
  }

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Case] =
    Try(startTraversal.getByNumber(name.toInt)).getOrElse(startTraversal.limit(0))

  def getCustomField(`case`: Case with Entity, customFieldIdOrName: EntityIdOrName)(implicit graph: Graph): Option[RichCustomField] =
    get(`case`).customFields(customFieldIdOrName).richCustomField.headOption

  def updateCustomField(
      `case`: Case with Entity,
      customFieldValues: Seq[(CustomField, Any, Option[Int])]
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val customFieldNames = customFieldValues.map(_._1.name)
    get(`case`)
      .richCustomFields
      .toIterator
      .filterNot(rcf => customFieldNames.contains(rcf.name))
      .foreach(rcf => get(`case`).customFields(EntityName(rcf.name)).remove())
    customFieldValues
      .toTry { case (cf, v, o) => setOrCreateCustomField(`case`, EntityName(cf.name), Some(v), o) }
      .map(_ => ())
  }

  def setOrCreateCustomField(`case`: Case with Entity, customFieldIdOrName: EntityIdOrName, value: Option[Any], order: Option[Int])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
    val cfv = get(`case`).customFields(customFieldIdOrName)
    if (cfv.clone().exists)
      cfv.setValue(value)
    else
      createCustomField(`case`, customFieldIdOrName, value, order).map(_ => ())
  }

  def createCustomField(
      `case`: Case with Entity,
      customFieldIdOrName: EntityIdOrName,
      customFieldValue: Option[Any],
      order: Option[Int]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    for {
      cf   <- customFieldSrv.getOrFail(customFieldIdOrName)
      ccf  <- CustomFieldType.map(cf.`type`).setValue(CaseCustomField().order_=(order), customFieldValue)
      ccfe <- caseCustomFieldSrv.create(ccf, `case`, cf)
    } yield RichCustomField(cf, ccfe)

  def setImpactStatus(
      `case`: Case with Entity,
      impactStatus: String
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    impactStatusSrv.getOrFail(EntityIdOrName(impactStatus)).flatMap(setImpactStatus(`case`, _))

  def setImpactStatus(
      `case`: Case with Entity,
      impactStatus: ImpactStatus with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).unsetImpactStatus()
    caseImpactStatusSrv.create(CaseImpactStatus(), `case`, impactStatus)
    auditSrv.`case`.update(`case`, Json.obj("impactStatus" -> impactStatus.value))
  }

  def unsetImpactStatus(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).unsetImpactStatus()
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
    get(`case`).unsetResolutionStatus()
    caseResolutionStatusSrv.create(CaseResolutionStatus(), `case`, resolutionStatus)
    auditSrv.`case`.update(`case`, Json.obj("resolutionStatus" -> resolutionStatus.value))
  }

  def unsetResolutionStatus(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).unsetResolutionStatus()
    auditSrv.`case`.update(`case`, Json.obj("resolutionStatus" -> JsNull))
  }

  def assign(`case`: Case with Entity, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).unassign()
    caseUserSrv.create(CaseUser(), `case`, user)
    auditSrv.`case`.update(`case`, Json.obj("owner" -> user.login))
  }

  def unassign(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).unassign()
    auditSrv.`case`.update(`case`, Json.obj("owner" -> JsNull))
  }

  def merge(cases: Seq[Case with Entity])(implicit graph: Graph, authContext: AuthContext): Try[RichCase] = {
    val mergedCase = Case(
      nextCaseNumber,
      cases.map(_.title).mkString(" / "),
      cases.map(_.description).mkString("\n\n"),
      cases.map(_.severity).max,
      cases.map(_.startDate).min,
      None,
      cases.exists(_.flag),
      cases.map(_.tlp).max,
      cases.map(_.pap).max,
      CaseStatus.Open,
      cases.map(_.summary).fold(None)((s1, s2) => (s1 ++ s2).reduceOption(_ + "\n\n" + _))
    )

    val tags = cases.flatMap(c => get(c).tags.toSeq)
    for {
      user     <- userSrv.get(EntityIdOrName(authContext.userId)).getOrFail("User")
      orga     <- organisationSrv.get(authContext.organisation).getOrFail("Organisation")
      richCase <- create(mergedCase, Some(user), orga, tags.toSet, Seq(), None, Seq())
      _ <- cases.toTry { c =>
        for {
          _ <-
            get(c)
              .tasks
              .richTask
              .toList
              .toTry(shareSrv.shareTask(_, richCase.`case`, orga))
          _ <-
            get(c)
              .observables
              .richObservable
              .toList
              .toTry(shareSrv.shareObservable(_, richCase.`case`, orga))
          _ <-
            get(c)
              .procedure
              .toList
              .toTry(caseProcedureSrv.create(CaseProcedure(), richCase.`case`, _))
          _ <-
            get(c)
              .richCustomFields
              .toList
              .toTry(c => createCustomField(richCase.`case`, EntityIdOrName(c.customField.name), c.value, c.order))
        } yield Success(())
      }
      _ = cases.map(remove(_))
    } yield richCase
  }
}

object CaseOps {

  implicit class CaseOpsDefs(traversal: Traversal.V[Case]) {

    def resolutionStatus: Traversal.V[ResolutionStatus] = traversal.out[CaseResolutionStatus].v[ResolutionStatus]

    def get(idOrName: EntityIdOrName): Traversal.V[Case] =
      idOrName.fold(traversal.getByIds(_), n => getByNumber(n.toInt))

    def getByNumber(caseNumber: Int): Traversal.V[Case] = traversal.has(_.number, caseNumber)

    def visible(implicit authContext: AuthContext): Traversal.V[Case] = visible(authContext.organisation)

    def visible(organisationIdOrName: EntityIdOrName): Traversal.V[Case] =
      traversal.filter(_.organisations.get(organisationIdOrName))

    def assignee: Traversal.V[User] = traversal.out[CaseUser].v[User]

    def can(permission: Permission)(implicit authContext: AuthContext): Traversal.V[Case] =
      if (authContext.permissions.contains(permission))
        traversal.filter(_.shares.filter(_.profile.has(_.permissions, permission)).organisation.current)
      else
        traversal.limit(0)

    def getLast: Traversal.V[Case] =
      traversal.sort(_.by("number", Order.desc))

    def richCaseWithCustomRenderer[D, G, C <: Converter[D, G]](
        entityRenderer: Traversal.V[Case] => Traversal[D, G, C]
    )(implicit authContext: AuthContext): Traversal[(RichCase, D), JMap[String, Any], Converter[(RichCase, D), JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.tags.v[Tag].fold)
            .by(_.impactStatus.value(_.value).fold)
            .by(_.resolutionStatus.value(_.value).fold)
            .by(_.assignee.value(_.login).fold)
            .by(_.richCustomFields.fold)
            .by(entityRenderer)
            .by(_.userPermissions)
        )
        .domainMap {
          case (caze, tags, impactStatus, resolutionStatus, user, customFields, renderedEntity, userPermissions) =>
            RichCase(
              caze,
              tags,
              impactStatus.headOption,
              resolutionStatus.headOption,
              user.headOption,
              customFields,
              userPermissions
            ) -> renderedEntity
        }

    def customFields(idOrName: EntityIdOrName): Traversal.E[CaseCustomField] =
      idOrName
        .fold(
          id => traversal.outE[CaseCustomField].filter(_.inV.getByIds(id)),
          name => traversal.outE[CaseCustomField].filter(_.inV.v[CustomField].has(_.name, name))
        )

    def customFields: Traversal.E[CaseCustomField] = traversal.outE[CaseCustomField]

    def richCustomFields: Traversal[RichCustomField, JMap[String, Any], Converter[RichCustomField, JMap[String, Any]]] =
      traversal
        .outE[CaseCustomField]
        .project(_.by.by(_.inV.v[CustomField]))
        .domainMap {
          case (cfv, cf) => RichCustomField(cf, cfv)
        }

    def share(implicit authContext: AuthContext): Traversal.V[Share] = share(authContext.organisation)

    def share(organisation: EntityIdOrName): Traversal.V[Share] =
      shares.filter(_.organisation.get(organisation)).v[Share]

    def shares: Traversal.V[Share] = traversal.in[ShareCase].v[Share]

    def organisations: Traversal.V[Organisation] = traversal.in[ShareCase].in[OrganisationShare].v[Organisation]

    def organisations(permission: Permission): Traversal.V[Organisation] =
      shares.filter(_.profile.has(_.permissions, permission)).organisation

    def userPermissions(implicit authContext: AuthContext): Traversal[Set[Permission], Vertex, Converter[Set[Permission], Vertex]] =
      traversal
        .share(authContext.organisation)
        .profile
        .domainMap(profile => profile.permissions & authContext.permissions)

    def origin: Traversal.V[Organisation] = shares.has(_.owner, true).organisation

    def audits(implicit authContext: AuthContext): Traversal.V[Audit] = audits(authContext.organisation)

    def audits(organisationIdOrName: EntityIdOrName): Traversal.V[Audit] =
      traversal
        .unionFlat(_.visible(organisationIdOrName), _.observables(organisationIdOrName), _.tasks(organisationIdOrName), _.share(organisationIdOrName))
        .in[AuditContext]
        .v[Audit]

    // Warning: this method doesn't generate audit log
    def unassign(): Unit =
      traversal.outE[CaseUser].remove()

    def unsetResolutionStatus(): Unit =
      traversal.outE[CaseResolutionStatus].remove()

    def unsetImpactStatus(): Unit =
      traversal.outE[CaseImpactStatus].remove()

    def removeTags(tags: Set[Tag with Entity]): Unit =
      if (tags.nonEmpty)
        traversal.outE[CaseTag].filter(_.otherV.hasId(tags.map(_._id).toSeq: _*)).remove()

    def linkedCases(implicit authContext: AuthContext): Seq[(RichCase, Seq[RichObservable])] = {
      val originCaseLabel = StepLabel.v[Case]
      val observableLabel = StepLabel.v[Observable]
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
        .toSeq
    }

    def richCase(implicit authContext: AuthContext): Traversal[RichCase, JMap[String, Any], Converter[RichCase, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.tags.fold)
            .by(_.impactStatus.value(_.value).fold)
            .by(_.resolutionStatus.value(_.value).fold)
            .by(_.assignee.value(_.login).fold)
            .by(_.richCustomFields.fold)
            .by(_.userPermissions)
        )
        .domainMap {
          case (caze, tags, impactStatus, resolutionStatus, user, customFields, userPermissions) =>
            RichCase(
              caze,
              tags,
              impactStatus.headOption,
              resolutionStatus.headOption,
              user.headOption,
              customFields,
              userPermissions
            )
        }

    def user: Traversal.V[User] = traversal.out[CaseUser].v[User]

    def richCaseWithoutPerms: Traversal[RichCase, JMap[String, Any], Converter[RichCase, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.tags.fold)
            .by(_.impactStatus.value(_.value).fold)
            .by(_.resolutionStatus.value(_.value).fold)
            .by(_.assignee.value(_.login).fold)
            .by(_.richCustomFields.fold)
        )
        .domainMap {
          case (caze, tags, impactStatus, resolutionStatus, user, customFields) =>
            RichCase(
              caze,
              tags,
              impactStatus.headOption,
              resolutionStatus.headOption,
              user.headOption,
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

class CaseIntegrityCheckOps @Inject() (@Named("with-thehive-schema") val db: Database, val service: CaseSrv) extends IntegrityCheckOps[Case] {
  def removeDuplicates(): Unit =
    duplicateEntities
      .foreach { entities =>
        db.tryTransaction { implicit graph =>
          resolve(entities)
        }
      }

  override def resolve(entities: Seq[Case with Entity])(implicit graph: Graph): Try[Unit] = {
    val nextNumber = service.nextCaseNumber
    firstCreatedEntity(entities).foreach(
      _._2
        .flatMap(service.get(_).setConverter[Vertex, Converter.Identity[Vertex]](Converter.identity).headOption)
        .zipWithIndex
        .foreach {
          case (vertex, index) =>
            UMapping.int.setProperty(vertex, "number", nextNumber + index)
        }
    )
    Success(())
  }
}
