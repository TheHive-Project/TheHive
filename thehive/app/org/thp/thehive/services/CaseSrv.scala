package org.thp.thehive.services

import java.util.{Map => JMap}

import akka.actor.ActorRef
import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.{Order, P}
import org.apache.tinkerpop.gremlin.structure.{Graph, Vertex}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FPathElem
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, StepLabel, Traversal}
import org.thp.scalligraph.{CreateError, RichOptionTry, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CustomFieldOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import play.api.libs.json.{JsNull, JsObject, Json}

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
    observableSrv: ObservableSrv,
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
  val mergedFromSrv           = new EdgeSrv[MergedFrom, Case, Case]

  override def createEntity(e: Case)(implicit graph: Graph, authContext: AuthContext): Try[Case with Entity] =
    super.createEntity(e).map { `case` =>
      integrityCheckActor ! IntegrityCheckActor.EntityAdded("Case")
      `case`
    }

  def create(
      `case`: Case,
      user: Option[User with Entity],
      organisation: Organisation with Entity,
      tags: Set[Tag with Entity],
      customFields: Seq[(String, Option[Any], Option[Int])],
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
      caseTemplateCustomFields =
        caseTemplate
          .fold[Seq[RichCustomField]](Nil)(_.customFields)
          .map(cf => (cf.name, cf.value, cf.order))
      cfs <- (caseTemplateCustomFields ++ customFields).toTry { case (name, value, order) => createCustomField(createdCase, name, value, order) }
      caseTemplateTags = caseTemplate.fold[Seq[Tag with Entity]](Nil)(_.tags)
      allTags          = tags ++ caseTemplateTags
      _ <- allTags.toTry(t => caseTagSrv.create(CaseTag(), createdCase, t))
      richCase = RichCase(createdCase, allTags.toSeq, None, None, Some(assignee.login), cfs, authContext.permissions)
      _ <- auditSrv.`case`.create(createdCase, richCase.toJson)
    } yield richCase

  def nextCaseNumber(implicit graph: Graph): Int = startTraversal.getLast.headOption.fold(0)(_.number) + 1

  override def exists(e: Case)(implicit graph: Graph): Boolean = startTraversal.getByNumber(e.number).exists

  override def update(
      traversal: Traversal.V[Case],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Case], JsObject)] = {
    val closeCase = PropertyUpdater(FPathElem("closeCase"), "") { (vertex, _, _, _) =>
      get(vertex)
        .tasks
        .or(_.has("status", "Waiting"), _.has("status", "InProgress"))
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

  def updateTags(`case`: Case with Entity, tags: Set[Tag with Entity])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
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
    val alreadyExistInThatCase = observableSrv
      .get(richObservable.observable)
      .similar
      .visible
      .`case`
      .hasId(`case`._id)
      .exists || get(`case`).observables.filter(_.hasId(richObservable.observable._id)).exists
    if (alreadyExistInThatCase)
      Failure(CreateError("Observable already exists"))
    else
      for {
        organisation <- organisationSrv.getOrFail(authContext.organisation)
        _            <- shareSrv.shareObservable(richObservable, `case`, organisation)
      } yield ()
  }

  def remove(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      organisation <- organisationSrv.getOrFail(authContext.organisation)
      _            <- auditSrv.`case`.delete(`case`, organisation)
    } yield {
      get(`case`).share.remove()
      get(`case`).remove()
    }

  override def get(idOrNumber: String)(implicit graph: Graph): Traversal.V[Case] =
    Success(idOrNumber)
      .filter(_.headOption.contains('#'))
      .map(_.tail.toInt)
      .map(startTraversal.getByNumber(_))
      .getOrElse(super.getByIds(idOrNumber))

  def getCustomField(`case`: Case with Entity, customFieldName: String)(implicit graph: Graph): Option[RichCustomField] =
    get(`case`).customFields(customFieldName).richCustomField.headOption

  def updateCustomField(
      `case`: Case with Entity,
      customFieldValues: Seq[(CustomField, Any, Option[Int])]
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val customFieldNames = customFieldValues.map(_._1.name)
    get(`case`)
      .richCustomFields
      .toIterator
      .filterNot(rcf => customFieldNames.contains(rcf.name))
      .foreach(rcf => get(`case`).customFields(rcf.name).remove())
    customFieldValues
      .toTry { case (cf, v, o) => setOrCreateCustomField(`case`, cf.name, Some(v), o) }
      .map(_ => ())
  }

  def setOrCreateCustomField(`case`: Case with Entity, customFieldName: String, value: Option[Any], order: Option[Int])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
    val cfv = get(`case`).customFields(customFieldName)
    if (cfv.clone().exists)
      cfv.setValue(value)
    else
      createCustomField(`case`, customFieldName, value, order).map(_ => ())
  }

  def createCustomField(
      `case`: Case with Entity,
      customFieldName: String,
      customFieldValue: Option[Any],
      order: Option[Int]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    for {
      cf   <- customFieldSrv.getOrFail(customFieldName)
      ccf  <- CustomFieldType.map(cf.`type`).setValue(CaseCustomField(), customFieldValue).map(_.order_=(order))
      ccfe <- caseCustomFieldSrv.create(ccf, `case`, cf)
    } yield RichCustomField(cf, ccfe)

  def setImpactStatus(
      `case`: Case with Entity,
      impactStatus: String
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    impactStatusSrv.getOrFail(impactStatus).flatMap(setImpactStatus(`case`, _))

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
    resolutionStatusSrv.getOrFail(resolutionStatus).flatMap(setResolutionStatus(`case`, _))

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

  def merge(cases: Seq[Case with Entity])(implicit graph: Graph, authContext: AuthContext): RichCase = ???
//  {
//    val summaries         = cases.flatMap(_.summary)
//    val user              = userSrv.getOrFail(authContext.userId)
//    val organisation      = organisationSrv.getOrFail(authContext.organisation)
//    val caseTaskSrv       = new EdgeSrv[CaseTask, Case, Task]
//    val caseObservableSrv = new EdgeSrv[CaseObservable, Case, Observable] nop
//
//    val mergedCase = create(
//      Case(
//        nextCaseNumber,
//        cases.map(_.title).mkString(" / "),
//        cases.map(_.description).mkString("\n\n"),
//        cases.map(_.severity).max,
//        cases.map(_.startDate).min,
//        None,
//        cases.flatMap(_.tags).distinct,
//        cases.exists(_.flag),
//        cases.map(_.tlp).max,
//        cases.map(_.pap).max,
//        CaseStatus.open,
//        if (summaries.isEmpty) None else Some(summaries.mkString("\n\n"))
//      ))
//    caseUserSrv.create(CaseUser(), mergedCase, user)
//    caseOrganisationSrv.create(CaseOrganisation(), mergedCase, organisation)
//    cases
//      .map(get)
//      .flatMap(_.customFields().toList
//      .groupBy(_.name)
//      .foreach {
//        case (name, l) ⇒
//          val values = l.collect { case cfwv: CustomFieldWithValue if cfwv.value.isDefined ⇒ cfwv.value.get }
//          val cf     = customFieldSrv.getOrFail(name)
//          val caseCustomField =
//            if (values.size == 1) cf.`type`.setValue(CaseCustomField(), values.head)
//            else CaseCustomField()
//          caseCustomFieldSrv.create(caseCustomField, mergedCase, cf)
//      }
//
//    cases.foreach(mergedFromSrv.create(MergedFrom(), mergedCase, _))
//
//    cases
//      .map(get)
//      .flatMap(_.tasks.toList
//      .foreach(task ⇒ caseTaskSrv.create(CaseTask(), task, mergedCase))
//
//    cases
//      .map(get)
//      .flatMap(_.observables.toList
//      .foreach(observable ⇒ observableCaseSrv.create(ObservableCase(), observable, mergedCase))
//
//    get(mergedCase).richCase.head
//  }
}

object CaseOps {

  implicit class CaseOpsDefs(traversal: Traversal.V[Case]) {

    def resolutionStatus: Traversal.V[ResolutionStatus] = traversal.out[CaseResolutionStatus].v[ResolutionStatus]

    def get(id: String): Traversal.V[Case] =
      Success(id)
        .filter(_.headOption.contains('#'))
        .map(_.tail.toInt)
        .map(getByNumber)
        .getOrElse(traversal.getByIds(id))

    def getByNumber(caseNumber: Int): Traversal.V[Case] = traversal.has("number", caseNumber)

    def visible(implicit authContext: AuthContext): Traversal.V[Case] = visible(authContext.organisation)

    def visible(organisationName: String): Traversal.V[Case] =
      traversal.filter(_.in[ShareCase].in[OrganisationShare].has("name", organisationName))

    def assignee: Traversal.V[User] = traversal.out[CaseUser].v[User]

    def can(permission: Permission)(implicit authContext: AuthContext): Traversal.V[Case] =
      if (authContext.permissions.contains(permission))
        traversal.filter(
          _.in[ShareCase]
            .filter(_.out[ShareProfile].has("permissions", permission))
            .in[OrganisationShare]
            .has("name", authContext.organisation)
        )
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

    def customFields(name: String): Traversal.E[CaseCustomField] =
      traversal.outE[CaseCustomField].filter(_.inV.has("name", name)).e[CaseCustomField]

    def customFields: Traversal.E[CaseCustomField] =
      traversal.outE[CaseCustomField].e[CaseCustomField]

    def richCustomFields: Traversal[RichCustomField, JMap[String, Any], Converter[RichCustomField, JMap[String, Any]]] =
      traversal
        .outE[CaseCustomField]
        .e[CaseCustomField]
        .project(_.by.by(_.inV.v[CustomField]))
        .domainMap {
          case (cfv, cf) => RichCustomField(cf, cfv)
        }

    def share(implicit authContext: AuthContext): Traversal.V[Share] = share(authContext.organisation)

    def share(organisationName: String): Traversal.V[Share] =
      traversal.in[ShareCase].filter(_.in[OrganisationShare].has("name", organisationName)).v[Share]

    def shares: Traversal.V[Share] = traversal.in[ShareCase].v[Share]

    def organisations: Traversal.V[Organisation] = traversal.in[ShareCase].in[OrganisationShare].v[Organisation]

    def organisations(permission: Permission): Traversal.V[Organisation] =
      traversal.in[ShareCase].filter(_.out[ShareProfile].has("permissions", permission)).in[OrganisationShare].v[Organisation]

    def userPermissions(implicit authContext: AuthContext): Traversal[Set[Permission], Vertex, Converter[Set[Permission], Vertex]] =
      traversal
        .share(authContext.organisation)
        .profile
        .domainMap(profile => profile.permissions & authContext.permissions)

    def origin: Traversal.V[Organisation] = traversal.in[ShareCase].has("owner", true).in[OrganisationShare].v[Organisation]

    def audits(implicit authContext: AuthContext): Traversal.V[Audit] = audits(authContext.organisation)

    def audits(organisationName: String): Traversal.V[Audit] =
      traversal
        .unionFlat(_.visible(organisationName), _.observables(organisationName), _.tasks(organisationName), _.share(organisationName))
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
        .as(observableLabel)
        .out[ObservableData]
        .in[ObservableData]
        .in[ShareObservable]
        .filter(
          _.in[OrganisationShare]
            .has("name", authContext.organisation)
        )
        .out[ShareCase]
        .where(P.neq(originCaseLabel.name))
        .group(_.by, _.by(_.select(observableLabel).richObservable.fold))
        .unfold
        .project(_.by(_.selectKeys.v[Case].richCase).by(_.selectValues))
        .toSeq
    }

    def richCase(implicit authContext: AuthContext): Traversal[RichCase, JMap[String, Any], Converter[RichCase, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.tags.v[Tag].fold)
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

    def tasks(organisationName: String): Traversal.V[Task] =
      share(organisationName).tasks

    def observables(implicit authContext: AuthContext): Traversal.V[Observable] = observables(authContext.organisation)

    def observables(organisationName: String): Traversal.V[Observable] =
      share(organisationName).observables

    def assignableUsers(implicit authContext: AuthContext): Traversal.V[User] =
      organisations(Permissions.manageCase)
        .visible
        .users(Permissions.manageCase)
        .dedup

    def alert: Traversal.V[Alert] = traversal.in[AlertCase].v[Alert]
  }

//  implicit class CaseCustomFieldsOpsDefs(traversal: Traversal.E[CaseCustomField]) extends CustomFieldValueOpsDefs(traversal)
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
