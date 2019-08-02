package org.thp.thehive.services

import java.util.{Date, List => JList}

import scala.collection.JavaConverters._
import scala.util.Try

import play.api.libs.json.{JsObject, Json}

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Path
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services.{EdgeSrv, _}
import org.thp.scalligraph.{EntitySteps, InternalError, RichJMap, RichOptionTry, RichSeq}
import org.thp.thehive.models._
import shapeless.HNil

@Singleton
class AlertSrv @Inject()(
    caseSrv: CaseSrv,
    tagSrv: TagSrv,
    customFieldSrv: CustomFieldSrv,
    caseTemplateSrv: CaseTemplateSrv,
    observableSrv: ObservableSrv,
    taskSrv: TaskSrv,
    userSrv: UserSrv,
    auditSrv: AuditSrv
)(
    implicit db: Database
) extends VertexSrv[Alert, AlertSteps] {

  val alertTagSrv          = new EdgeSrv[AlertTag, Alert, Tag]
  val alertCustomFieldSrv  = new EdgeSrv[AlertCustomField, Alert, CustomField]
  val alertOrganisationSrv = new EdgeSrv[AlertOrganisation, Alert, Organisation]
  val alertCaseSrv         = new EdgeSrv[AlertCase, Alert, Case]
  val alertCaseTemplateSrv = new EdgeSrv[AlertCaseTemplate, Alert, CaseTemplate]
  val alertObservableSrv   = new EdgeSrv[AlertObservable, Alert, Observable]

  override def get(id: String)(implicit graph: Graph): AlertSteps =
    id.split(';') match {
      case Array(tpe, source, sourceRef) => initSteps.getBySourceId(tpe, source, sourceRef)
      case _                             => super.get(id)
    }

  def create(
      alert: Alert,
      organisation: Organisation with Entity,
      tagNames: Set[String],
      customFields: Map[String, Option[Any]],
      caseTemplate: Option[RichCaseTemplate]
  )(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichAlert] = {

    def createCustomFields(alert: Alert with Entity) =
      customFields
        .toSeq
        .toTry {
          case (name, Some(value)) =>
            for {
              cf               <- customFieldSrv.getOrFail(name)
              acf              <- cf.`type`.setValue(AlertCustomField(), value)
              alertCustomField <- alertCustomFieldSrv.create(acf, alert, cf)
            } yield CustomFieldWithValue(cf, alertCustomField)
          case (name, _) =>
            for {
              cf               <- customFieldSrv.getOrFail(name)
              alertCustomField <- alertCustomFieldSrv.create(AlertCustomField(), alert, cf)
            } yield CustomFieldWithValue(cf, alertCustomField)
        }

    for {
      createdAlert <- create(alert)
      _            <- alertOrganisationSrv.create(AlertOrganisation(), createdAlert, organisation)
      _            <- caseTemplate.map(ct => alertCaseTemplateSrv.create(AlertCaseTemplate(), createdAlert, ct.caseTemplate)).flip
      tags         <- tagNames.toTry(t => tagSrv.getOrCreate(t))
      _            <- tags.toTry(t => alertTagSrv.create(AlertTag(), createdAlert, t))
      cfs          <- createCustomFields(createdAlert)
      _            <- auditSrv.alert.create(createdAlert)
    } yield RichAlert(createdAlert, organisation.name, tags, cfs, None, caseTemplate.map(_.name))
  }

  override def update(
      steps: AlertSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(AlertSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (alertSteps, updatedFields) =>
        alertSteps
          .clone()
          .getOrFail()
          .flatMap(auditSrv.alert.update(_, updatedFields))
    }

  def updateTags(alert: Alert with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val (tagsToAdd, tagsToRemove) = get(alert)
      .tags
      .toIterator
      .foldLeft((tags, Set.empty[String])) {
        case ((toAdd, toRemove), t) if toAdd.contains(t.name) => (toAdd - t.name, toRemove)
        case ((toAdd, toRemove), t)                           => (toAdd, toRemove + t.name)
      }
    for {
      createdTags <- tagsToAdd.toTry(tagSrv.getOrCreate(_))
      _           <- createdTags.toTry(alertTagSrv.create(AlertTag(), alert, _))
      _ = get(alert).removeTags(tagsToRemove)
      _ <- auditSrv.alert.update(alert, Json.obj("tags" -> tags))
    } yield ()
  }

  def addTags(alert: Alert with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val currentTags = get(alert)
      .tags
      .toList
      .map(_.name)
      .toSet
    for {
      createdTags <- (tags -- currentTags).toTry(tagSrv.getOrCreate(_))
      _           <- createdTags.toTry(alertTagSrv.create(AlertTag(), alert, _))
      _           <- auditSrv.alert.update(alert, Json.obj("tags" -> (currentTags ++ tags)))
    } yield ()
  }

  def addObservable(alert: Alert with Entity, observable: Observable with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      _ <- alertObservableSrv.create(AlertObservable(), alert, observable)
      _ <- auditSrv.observableInAlert.create(observable, alert)
    } yield ()

  def setCustomField(alert: Alert with Entity, customFieldName: String, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    customFieldSrv.getOrFail(customFieldName).flatMap(cf => setCustomField(alert, cf, value))

  def setCustomField(alert: Alert with Entity, customField: CustomField with Entity, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    getCustomField(alert, customField.name)
      .fold(addCustomField(alert, customField, value))(updateCustomField(alert, _, value))
      .flatMap { _ =>
        auditSrv.alert.update(alert, Json.obj(s"customField.${customField.name}" -> value.toString))
      }

  private def addCustomField(alert: Alert with Entity, customField: CustomField with Entity, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      alertCustomField <- customField.`type`.asInstanceOf[CustomFieldType[Any]].setValue(AlertCustomField(), value)
      _                <- alertCustomFieldSrv.create(alertCustomField, alert, customField)
    } yield ()

  private def updateCustomField(alert: Alert with Entity, customFieldWithValue: CustomFieldWithValue, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    alertCustomFieldSrv
      .get(customFieldWithValue.customFieldValue._id)
      .update((customFieldWithValue.`type`.name + "Value") -> Some(value))
      .map(_ => ())

  def getCustomField(alert: Alert with Entity, customFieldName: String)(implicit graph: Graph): Option[CustomFieldWithValue] =
    get(alert).customFields(Some(customFieldName)).headOption()

  def markAsUnread(alertId: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      alert <- get(alertId).update("read" -> false)
      _     <- auditSrv.alert.update(alert, Json.obj("read" -> false))
    } yield ()

  def markAsRead(alertId: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      alert <- get(alertId).update("read" -> true)
      _     <- auditSrv.alert.update(alert, Json.obj("read" -> true))
    } yield ()

  def followAlert(alertId: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      alert <- get(alertId).update("follow" -> true)
      _     <- auditSrv.alert.update(alert, Json.obj("follow" -> true))
    } yield ()

  def unfollowAlert(alertId: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      alert <- get(alertId).update("follow" -> false)
      _     <- auditSrv.alert.update(alert, Json.obj("follow" -> false))
    } yield ()

  def createCase(alert: RichAlert, user: Option[User with Entity], organisation: Organisation with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichCase] =
    for {
      caseTemplate <- alert
        .caseTemplate
        .map(caseTemplateSrv.get(_).richCaseTemplate.getOrFail())
        .flip
      customField = alert.customFields.map(f => f.name -> f.value).toMap
      case0 = Case(
        number = 0,
        title = caseTemplate.flatMap(_.titlePrefix).getOrElse("") + alert.title,
        description = alert.description,
        severity = alert.severity,
        startDate = new Date,
        endDate = None,
        flag = alert.flag,
        tlp = alert.tlp,
        pap = alert.pap,
        status = CaseStatus.Open,
        summary = None
      )

      createdCase <- caseSrv.create(case0, user, organisation, alert.tags.map(_.name).toSet, customField, caseTemplate)
      _           <- importObservables(alert.alert, createdCase.`case`)
      _           <- alertCaseSrv.create(AlertCase(), alert.alert, createdCase.`case`)
      _           <- markAsRead(alert._id)
      _           <- auditSrv.`case`.create(createdCase.`case`) // TODO add from alert ?
    } yield createdCase

  def mergeInCase(alertId: String, caseId: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      alert <- getOrFail(alertId)
      case0 <- caseSrv.getOrFail(caseId)
      description = case0.description + s"\n  \n#### Merged with alert #${alert.sourceRef} ${alert.title}\n\n${alert.description.trim}"
      _ <- caseSrv.get(caseId).update("description" -> description)
      _ <- importObservables(alert, case0)
    } yield () // TODO add special audit ?

  def importObservables(alert: Alert with Entity, `case`: Case with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    get(alert)
      .observables
      .richObservable
      .toIterator
      .toTry { richObservable =>
        observableSrv
          .duplicate(richObservable)
          .flatMap(duplicatedObservable => caseSrv.addObservable(`case`, duplicatedObservable.observable))
      }
      .map(_ => ())

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AlertSteps = new AlertSteps(raw)
}

@EntitySteps[Alert]
class AlertSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Alert, AlertSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): AlertSteps = new AlertSteps(raw)

  override def get(id: String): AlertSteps =
    id.split(';') match {
      case Array(tpe, source, sourceRef) => getBySourceId(tpe, source, sourceRef)
      case _                             => getById(id)
    }

  def getById(id: String): AlertSteps = newInstance(raw.hasId(id))

  def getBySourceId(`type`: String, source: String, sourceRef: String): AlertSteps =
    newInstance(
      raw
        .has(Key("type") of `type`)
        .has(Key("source") of source)
        .has(Key("sourceRef") of sourceRef)
    )

  def organisation: OrganisationSteps = new OrganisationSteps(raw.inTo[AlertOrganisation])

  def tags: TagSteps = new TagSteps(raw.outTo[AlertTag])

  def removeTags(tagNames: Set[String]): Unit = {
    raw.outToE[AlertTag].where(_.otherV().has(Key[String]("name"), P.within(tagNames))).drop().iterate()
    ()
  }

  def visible(implicit authContext: AuthContext): AlertSteps =
    filter(
      _.outTo[AlertOrganisation]
        .inTo[RoleOrganisation]
        .inTo[UserRole]
        .has(Key("login") of authContext.userId)
    )

  def can(permission: Permission)(implicit authContext: AuthContext): AlertSteps =
    filter(
      _.outTo[AlertOrganisation]
        .inTo[RoleOrganisation]
        .filter(_.outTo[RoleProfile].has(Key("permissions") of permission))
        .inTo[UserRole]
        .has(Key("login") of authContext.userId)
    )

  def alertUserOrganisation(permission: Permission)(implicit authContext: AuthContext): ScalarSteps[(RichAlert, Organisation with Entity)] = {
    val alertLabel            = StepLabel[Vertex]()
    val organisationLabel     = StepLabel[Vertex]()
    val tagLabel              = StepLabel[JList[Vertex]]()
    val customFieldLabel      = StepLabel[JList[Path]]()
    val caseIdLabel           = StepLabel[JList[AnyRef]]()
    val caseTemplateNameLabel = StepLabel[JList[String]]()
    new ScalarSteps(
      raw
        .asInstanceOf[GremlinScala.Aux[Vertex, HNil]]
        .`match`(
          _.as(alertLabel).out("AlertOrganisation").as(organisationLabel),
          _.as(alertLabel).out("AlertTag").fold().as(tagLabel),
          _.as(organisationLabel)
            .inTo[RoleOrganisation]
            .filter(_.outTo[RoleProfile].has(Key("permissions") of permission))
            .inTo[UserRole]
            .has(Key("login") of authContext.userId),
          _.as(alertLabel).outToE[AlertCustomField].inV().path.fold.as(customFieldLabel),
          _.as(alertLabel).outTo[AlertCase].id().fold.as(caseIdLabel),
          _.as(alertLabel).outTo[AlertCaseTemplate].values[String]("name").fold.as(caseTemplateNameLabel)
        )
        .select(alertLabel.name, organisationLabel.name, tagLabel.name, customFieldLabel.name, caseIdLabel.name, caseTemplateNameLabel.name)
        .map { resultMap =>
          val organisation = resultMap.getValue(organisationLabel).as[Organisation]
          val tags         = resultMap.getValue(tagLabel).asScala.map(_.as[Tag]).toSeq
          val customFieldValues = resultMap
            .getValue(customFieldLabel)
            .asScala
            .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
            .map {
              case List(acf, cf) => CustomFieldWithValue(cf.as[CustomField], acf.as[AlertCustomField])
              case _             => throw InternalError("Not possible")
            }
            .toSeq

          RichAlert(
            resultMap.getValue(alertLabel).as[Alert],
            organisation.name,
            tags,
            customFieldValues,
            atMostOneOf(resultMap.getValue(caseIdLabel)).map(_.toString),
            atMostOneOf(resultMap.getValue(caseTemplateNameLabel))
          ) -> organisation
        }
    )
  }

  def customFields(name: Option[String] = None): ScalarSteps[CustomFieldWithValue] = {
    val acfSteps: GremlinScala[Vertex] = raw
      .outToE[AlertCustomField]
      .inV()
    ScalarSteps(
      name
        .fold[GremlinScala[Vertex]](acfSteps)(n => acfSteps.has(Key("name") of n))
        .path
        .map(path => CustomFieldWithValue(path.get[Vertex](2).as[CustomField], path.get[Edge](1).as[AlertCustomField]))
    )
  }

  def observables: ObservableSteps = new ObservableSteps(raw.outTo[AlertObservable])

  def richAlert: ScalarSteps[RichAlert] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[AlertOrganisation].values[String]("name").fold))
            .and(By(__[Vertex].outTo[AlertTag].fold))
            .and(By(__[Vertex].outToE[AlertCustomField].inV().path.fold))
            .and(By(__[Vertex].outTo[AlertCase].id().fold))
            .and(By(__[Vertex].outTo[AlertCaseTemplate].values[String]("name").fold))
        )
        .map {
          case (alert, organisation, tags, customFields, caseId, caseTemplate) =>
            val customFieldValues = (customFields: JList[Path])
              .asScala
              .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
              .map {
                case List(acf, cf) => CustomFieldWithValue(cf.as[CustomField], acf.as[AlertCustomField])
                case _             => throw InternalError("Not possible")
              }
            RichAlert(
              alert.as[Alert],
              onlyOneOf[String](organisation),
              tags.asScala.map(_.as[Tag]).toSeq,
              customFieldValues.toSeq,
              atMostOneOf[AnyRef](caseId).map(_.toString),
              atMostOneOf[String](caseTemplate)
            )
        }
    )
}
