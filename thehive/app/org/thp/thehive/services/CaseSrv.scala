package org.thp.thehive.services

import akka.actor.ActorRef
import org.apache.tinkerpop.gremlin.process.traversal.{Order, P}
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.{FFile, FPathElem}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PredicateOps.PredicateOpsDefs
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal._
import org.thp.scalligraph.{EntityId, EntityIdOrName, EntityName, RichOptionTry, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CustomFieldOps._
import org.thp.thehive.services.DataOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.UserOps._
import play.api.cache.SyncCacheApi
import play.api.libs.json.{JsNull, JsObject, JsValue, Json}

import java.lang.{Long => JLong}
import java.util.{Date, List => JList, Map => JMap}
import javax.inject.{Inject, Named, Singleton}
import scala.util.{Success, Try}

@Singleton
class CaseSrv @Inject() (
    tagSrv: TagSrv,
    customFieldSrv: CustomFieldSrv,
    organisationSrv: OrganisationSrv,
    profileSrv: ProfileSrv,
    shareSrv: ShareSrv,
    taskSrv: TaskSrv,
    auditSrv: AuditSrv,
    resolutionStatusSrv: ResolutionStatusSrv,
    impactStatusSrv: ImpactStatusSrv,
    observableSrv: ObservableSrv,
    attachmentSrv: AttachmentSrv,
    @Named("integrity-check-actor") integrityCheckActor: ActorRef,
    cache: SyncCacheApi
) extends VertexSrv[Case] {

  val caseTagSrv              = new EdgeSrv[CaseTag, Case, Tag]
  val caseImpactStatusSrv     = new EdgeSrv[CaseImpactStatus, Case, ImpactStatus]
  val caseResolutionStatusSrv = new EdgeSrv[CaseResolutionStatus, Case, ResolutionStatus]
  val caseUserSrv             = new EdgeSrv[CaseUser, Case, User]
  val caseCustomFieldSrv      = new EdgeSrv[CaseCustomField, Case, CustomField]
  val caseCaseTemplateSrv     = new EdgeSrv[CaseCaseTemplate, Case, CaseTemplate]
  val mergedFromSrv           = new EdgeSrv[MergedFrom, Case, Case]

  override def createEntity(e: Case)(implicit graph: Graph, authContext: AuthContext): Try[Case with Entity] =
    super.createEntity(e).map { `case` =>
      integrityCheckActor ! EntityAdded("Case")
      `case`
    }

  def create(
      `case`: Case,
      assignee: Option[User with Entity],
      organisation: Organisation with Entity,
      customFields: Seq[InputCustomFieldValue],
      caseTemplate: Option[RichCaseTemplate],
      additionalTasks: Seq[Task]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCase] = {
    val caseNumber = if (`case`.number == 0) nextCaseNumber else `case`.number
    val tagNames   = (`case`.tags ++ caseTemplate.fold[Seq[String]](Nil)(_.tags)).distinct
    for {
      tags <- tagNames.toTry(tagSrv.getOrCreate)
      createdCase <- createEntity(
        `case`.copy(
          number = caseNumber,
          assignee = assignee.map(_.login),
          organisationIds = Seq(organisation._id),
          caseTemplate = caseTemplate.map(_.name),
          impactStatus = None,
          resolutionStatus = None,
          tags = tagNames
        )
      )
      _ <- assignee.map(u => caseUserSrv.create(CaseUser(), createdCase, u)).flip
      _ <- shareSrv.shareCase(owner = true, createdCase, organisation, profileSrv.orgAdmin)
      _ <- caseTemplate.map(ct => caseCaseTemplateSrv.create(CaseCaseTemplate(), createdCase, ct.caseTemplate)).flip
      _ <- caseTemplate.fold(additionalTasks)(_.tasks.map(_.task) ++ additionalTasks).toTry(task => createTask(createdCase, task))
      _ <- tags.toTry(caseTagSrv.create(CaseTag(), createdCase, _))

      caseTemplateCf =
        caseTemplate
          .fold[Seq[RichCustomField]](Seq())(_.customFields)
          .map(cf => InputCustomFieldValue(cf.name, cf.value, cf.order))
      cfs <- cleanCustomFields(caseTemplateCf, customFields).toTry {
        case InputCustomFieldValue(name, value, order) => createCustomField(createdCase, EntityIdOrName(name), value, order)
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

  def nextCaseNumber(implicit graph: Graph): Int = startTraversal.getLast.headOption.fold(0)(_.number) + 1

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

  def updateTags(`case`: Case with Entity, tags: Set[String])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[(Seq[Tag with Entity], Seq[Tag with Entity])] =
    for {
      tagsToAdd    <- (tags -- `case`.tags).toTry(tagSrv.getOrCreate)
      tagsToRemove <- (`case`.tags.toSet -- tags).toTry(tagSrv.getOrCreate)
      _            <- tagsToAdd.toTry(caseTagSrv.create(CaseTag(), `case`, _))
      _ = if (tags.nonEmpty) get(`case`).outE[CaseTag].filter(_.otherV.hasId(tagsToRemove.map(_._id): _*)).remove()
      _ <- get(`case`).update(_.tags, tags.toSeq).getOrFail("Case")
      _ <- auditSrv.`case`.update(`case`, Json.obj("tags" -> tags))
    } yield (tagsToAdd, tagsToRemove)

  def addTags(`case`: Case with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    updateTags(`case`, tags ++ `case`.tags).map(_ => ())

  def createTask(`case`: Case with Entity, task: Task)(implicit graph: Graph, authContext: AuthContext): Try[RichTask] =
    for {
      assignee <- task.assignee.map(u => get(`case`).assignableUsers.getByName(u).getOrFail("User")).flip
      task     <- taskSrv.create(task.copy(relatedId = `case`._id, organisationIds = Seq(organisationSrv.currentId)), assignee)
      _        <- shareSrv.shareTask(task, `case`, organisationSrv.currentId)
    } yield task

  def createObservable(`case`: Case with Entity, observable: Observable, data: String)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      createdObservable <- observableSrv.create(observable.copy(organisationIds = Seq(organisationSrv.currentId), relatedId = `case`._id), data)
      _                 <- shareSrv.shareObservable(createdObservable, `case`, organisationSrv.currentId)
    } yield createdObservable

  def createObservable(`case`: Case with Entity, observable: Observable, attachment: Attachment with Entity)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      createdObservable <- observableSrv.create(observable.copy(organisationIds = Seq(organisationSrv.currentId), relatedId = `case`._id), attachment)
      _                 <- shareSrv.shareObservable(createdObservable, `case`, organisationSrv.currentId)
    } yield createdObservable

  def createObservable(`case`: Case with Entity, observable: Observable, file: FFile)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    attachmentSrv.create(file).flatMap(attachment => createObservable(`case`, observable, attachment))

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
    Try(startTraversal.getByNumber(name.toInt)).getOrElse(startTraversal.empty)

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
    get(`case`).update(_.impactStatus, Some(impactStatus.value)).outE[CaseImpactStatus].remove()
    caseImpactStatusSrv.create(CaseImpactStatus(), `case`, impactStatus)
    auditSrv.`case`.update(`case`, Json.obj("impactStatus" -> impactStatus.value))
  }

  def unsetImpactStatus(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).update(_.impactStatus, None).outE[CaseImpactStatus].remove()
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
    get(`case`).update(_.resolutionStatus, Some(resolutionStatus.value)).outE[CaseResolutionStatus].remove()
    caseResolutionStatusSrv.create(CaseResolutionStatus(), `case`, resolutionStatus)
    auditSrv.`case`.update(`case`, Json.obj("resolutionStatus" -> resolutionStatus.value))
  }

  def unsetResolutionStatus(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).update(_.resolutionStatus, None).outE[CaseResolutionStatus].remove()
    auditSrv.`case`.update(`case`, Json.obj("resolutionStatus" -> JsNull))
  }

  def assign(`case`: Case with Entity, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).update(_.assignee, Some(user.login)).outE[CaseUser].remove()
    caseUserSrv.create(CaseUser(), `case`, user)
    auditSrv.`case`.update(`case`, Json.obj("owner" -> user.login))
  }

  def unassign(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).update(_.assignee, None).outE[CaseUser].remove()
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
//        case (name, l) =>
//          val values = l.collect { case cfwv: CustomFieldWithValue if cfwv.value.isDefined => cfwv.value.get }
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
//      .foreach(task => caseTaskSrv.create(CaseTask(), task, mergedCase))
//
//    cases
//      .map(get)
//      .flatMap(_.observables.toList
//      .foreach(observable => observableCaseSrv.create(ObservableCase(), observable, mergedCase))
//
//    get(mergedCase).richCase.head
//  }
}

object CaseOps {

  implicit class CaseOpsDefs(traversal: Traversal.V[Case]) {

    def resolutionStatus: Traversal.V[ResolutionStatus] = traversal.out[CaseResolutionStatus].v[ResolutionStatus]

    def get(idOrName: EntityIdOrName): Traversal.V[Case] =
      idOrName.fold(traversal.getByIds(_), n => getByNumber(n.toInt))

    def getByNumber(caseNumber: Int): Traversal.V[Case] = traversal.has(_.number, caseNumber)

    def visible(organisationSrv: OrganisationSrv)(implicit authContext: AuthContext): Traversal.V[Case] =
      traversal.has(_.organisationIds, organisationSrv.currentId(traversal.graph, authContext))

    def assignee: Traversal.V[User] = traversal.out[CaseUser].v[User]

    def assignedTo(userLogin: String*): Traversal.V[Case] =
      if (userLogin.isEmpty) traversal.empty
      else if (userLogin.size == 1) traversal.has(_.assignee, userLogin.head)
      else traversal.has(_.assignee, P.within(userLogin: _*))

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

    def customFieldFilter(customFieldSrv: CustomFieldSrv, customField: EntityIdOrName, predicate: P[JsValue]): Traversal.V[Case] =
      customFieldSrv
        .get(customField)(traversal.graph)
        .value(_.`type`)
        .headOption
        .map {
          case CustomFieldType.boolean => traversal.filter(_.customFields(customField).has(_.booleanValue, predicate.map(_.as[Boolean])))
          case CustomFieldType.date    => traversal.filter(_.customFields(customField).has(_.dateValue, predicate.map(_.as[Date])))
          case CustomFieldType.float   => traversal.filter(_.customFields(customField).has(_.floatValue, predicate.map(_.as[Double])))
          case CustomFieldType.integer => traversal.filter(_.customFields(customField).has(_.integerValue, predicate.map(_.as[Int])))
          case CustomFieldType.string  => traversal.filter(_.customFields(customField).has(_.stringValue, predicate.map(_.as[String])))
        }
        .getOrElse(traversal.empty)

    def hasCustomField(customFieldSrv: CustomFieldSrv, customField: EntityIdOrName): Traversal.V[Case] = {
      val cfFilter = (t: Traversal.V[CustomField]) => customField.fold(id => t.hasId(id), name => t.has(_.name, name))

      customFieldSrv
        .get(customField)(traversal.graph)
        .value(_.`type`)
        .headOption
        .map {
          case CustomFieldType.boolean => traversal.filter(t => cfFilter(t.outE[CaseCustomField].has(_.booleanValue).inV.v[CustomField]))
          case CustomFieldType.date    => traversal.filter(t => cfFilter(t.outE[CaseCustomField].has(_.dateValue).inV.v[CustomField]))
          case CustomFieldType.float   => traversal.filter(t => cfFilter(t.outE[CaseCustomField].has(_.floatValue).inV.v[CustomField]))
          case CustomFieldType.integer => traversal.filter(t => cfFilter(t.outE[CaseCustomField].has(_.integerValue).inV.v[CustomField]))
          case CustomFieldType.string  => traversal.filter(t => cfFilter(t.outE[CaseCustomField].has(_.stringValue).inV.v[CustomField]))
        }
        .getOrElse(traversal.empty)
    }

    def hasNotCustomField(customFieldSrv: CustomFieldSrv, customField: EntityIdOrName): Traversal.V[Case] = {
      val cfFilter = (t: Traversal.V[CustomField]) => customField.fold(id => t.hasId(id), name => t.has(_.name, name))

      customFieldSrv
        .get(customField)(traversal.graph)
        .value(_.`type`)
        .headOption
        .map {
          case CustomFieldType.boolean => traversal.filterNot(t => cfFilter(t.outE[CaseCustomField].has(_.booleanValue).inV.v[CustomField]))
          case CustomFieldType.date    => traversal.filterNot(t => cfFilter(t.outE[CaseCustomField].has(_.dateValue).inV.v[CustomField]))
          case CustomFieldType.float   => traversal.filterNot(t => cfFilter(t.outE[CaseCustomField].has(_.floatValue).inV.v[CustomField]))
          case CustomFieldType.integer => traversal.filterNot(t => cfFilter(t.outE[CaseCustomField].has(_.integerValue).inV.v[CustomField]))
          case CustomFieldType.string  => traversal.filterNot(t => cfFilter(t.outE[CaseCustomField].has(_.stringValue).inV.v[CustomField]))
        }
        .getOrElse(traversal.empty)
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

    def linkedCases(implicit authContext: AuthContext): Seq[(RichCase, Seq[RichObservable])] = {
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
        .toSeq
    }

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

class CaseIntegrityCheckOps @Inject() (val db: Database, val service: CaseSrv) extends IntegrityCheckOps[Case] {
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
