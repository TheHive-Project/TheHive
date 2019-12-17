package org.thp.thehive.services

import java.util.{Date, List => JList}

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

import play.api.libs.json.{JsObject, Json}

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Path
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.scalligraph.{CreateError, EntitySteps, InternalError, RichJMap, RichOptionTry, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._

@Singleton
class AlertSrv @Inject()(
    caseSrv: CaseSrv,
    tagSrv: TagSrv,
    organisationSrv: OrganisationSrv,
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

  override def get(idOrSource: String)(implicit graph: Graph): AlertSteps = idOrSource.split(';') match {
    case Array(tpe, source, sourceRef) => initSteps.getBySourceId(tpe, source, sourceRef)
    case _                             => super.getByIds(idOrSource)
  }

  def create(
      alert: Alert,
      organisation: Organisation with Entity,
      tagNames: Set[String],
      customFields: Map[String, Option[Any]],
      caseTemplate: Option[CaseTemplate with Entity]
  )(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichAlert] = {
    val alertAlreadyExist = organisationSrv.get(organisation).alerts.getBySourceId(alert.`type`, alert.source, alert.sourceRef).getCount
    if (alertAlreadyExist > 0)
      Failure(CreateError(s"Alert ${alert.`type`}:${alert.source}:${alert.sourceRef} already exist in organisation ${organisation.name}"))
    else
      for {
        createdAlert <- createEntity(alert)
        _            <- alertOrganisationSrv.create(AlertOrganisation(), createdAlert, organisation)
        _            <- caseTemplate.map(ct => alertCaseTemplateSrv.create(AlertCaseTemplate(), createdAlert, ct)).flip
        tags         <- tagNames.filterNot(_.isEmpty).toTry(tagSrv.getOrCreate)
        _            <- tags.toTry(t => alertTagSrv.create(AlertTag(), createdAlert, t))
        cfs          <- customFields.toTry { case (name, value) => createCustomField(createdAlert, name, value) }
        richAlert = RichAlert(createdAlert, organisation.name, tags, cfs, None, caseTemplate.map(_.name))
        _ <- auditSrv.alert.create(createdAlert, richAlert.toJson)
      } yield richAlert
  }

  override def update(
      steps: AlertSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(AlertSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (alertSteps, updatedFields) =>
        alertSteps
          .newInstance()
          .getOrFail()
          .flatMap(auditSrv.alert.update(_, updatedFields))
    }

  def updateTags(alert: Alert with Entity, tags: Set[Tag with Entity])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val (tagsToAdd, tagsToRemove) = get(alert)
      .tags
      .toIterator
      .foldLeft((tags, Set.empty[Tag with Entity])) {
        case ((toAdd, toRemove), t) if toAdd.contains(t) => (toAdd - t, toRemove)
        case ((toAdd, toRemove), t)                      => (toAdd, toRemove + t)
      }
    for {
//      createdTags <- tagsToAdd.toTry(tagSrv.getOrCreate)
      _ <- tagsToAdd.toTry(alertTagSrv.create(AlertTag(), alert, _))
      _ = get(alert).removeTags(tagsToRemove)
      _ <- auditSrv.alert.update(alert, Json.obj("tags" -> tags.map(_.toString)))
    } yield ()

  }

  def updateTagNames(alert: Alert with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    tags.toTry(tagSrv.getOrCreate).flatMap(t => updateTags(alert, t.toSet))

  def addTags(alert: Alert with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val currentTags = get(alert)
      .tags
      .toList
      .map(_.toString)
      .toSet
    for {
      createdTags <- (tags -- currentTags).toTry(tagSrv.getOrCreate)
      _           <- createdTags.toTry(alertTagSrv.create(AlertTag(), alert, _))
      _           <- auditSrv.alert.update(alert, Json.obj("tags" -> (currentTags ++ tags)))
    } yield ()
  }

  def addObservable(alert: Alert with Entity, richObservable: RichObservable)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
    val alreadyExistInThatCase = observableSrv
      .get(richObservable.observable)
      .similar
      .alert
      .hasId(alert._id)
      .exists()
    if (alreadyExistInThatCase)
      Failure(CreateError("Observable already exists"))
    else
      for {
        _ <- alertObservableSrv.create(AlertObservable(), alert, richObservable.observable)
        _ <- auditSrv.observableInAlert.create(richObservable.observable, alert, richObservable.toJson)
      } yield ()
  }

  def createCustomField(
      alert: Alert with Entity,
      customFieldName: String,
      customFieldValue: Option[Any]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    for {
      cf   <- customFieldSrv.getOrFail(customFieldName)
      ccf  <- CustomFieldType.map(cf.`type`).setValue(AlertCustomField(), customFieldValue)
      ccfe <- alertCustomFieldSrv.create(ccf, alert, cf)
    } yield RichCustomField(cf, ccfe)

  def setOrCreateCustomField(alert: Alert with Entity, customFieldName: String, value: Option[Any])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
    val cfv = get(alert).customFields(customFieldName)
    if (cfv.newInstance().exists())
      cfv.setValue(value)
    else
      createCustomField(alert, customFieldName, value).map(_ => ())
  }

  def getCustomField(alert: Alert with Entity, customFieldName: String)(implicit graph: Graph): Option[RichCustomField] =
    get(alert).customFields(customFieldName).richCustomField.headOption()

  def updateCustomField(
      alert: Alert with Entity,
      customFieldValues: Seq[(CustomField, Any)]
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val customFieldNames = customFieldValues.map(_._1.name)
    get(alert)
      .customFields
      .richCustomField
      .toIterator
      .filterNot(rcf => customFieldNames.contains(rcf.name))
      .foreach(rcf => get(alert).customFields(rcf.name).remove())
    customFieldValues
      .toTry { case (cf, v) => setOrCreateCustomField(alert, cf.name, Some(v)) }
      .map(_ => ())
  }

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
        flag = false,
        tlp = alert.tlp,
        pap = alert.pap,
        status = CaseStatus.Open,
        summary = None
      )

      createdCase <- caseSrv.create(case0, user, organisation, alert.tags.toSet, customField, caseTemplate, Nil)
      _           <- importObservables(alert.alert, createdCase.`case`)
      _           <- alertCaseSrv.create(AlertCase(), alert.alert, createdCase.`case`)
      _           <- markAsRead(alert._id)
    } yield createdCase

  def mergeInCase(alertId: String, caseId: String)(implicit graph: Graph, authContext: AuthContext): Try[Case with Entity] =
    for {
      alert       <- getOrFail(alertId)
      case0       <- caseSrv.getOrFail(caseId)
      updatedCase <- mergeInCase(alert, case0)
    } yield updatedCase

  def mergeInCase(alert: Alert with Entity, `case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Case with Entity] =
    for {
      _ <- caseSrv.addTags(`case`, get(alert).tags.toList.map(_.toString).toSet)
      description = `case`.description + s"\n  \n#### Merged with alert #${alert.sourceRef} ${alert.title}\n\n${alert.description.trim}"
      c <- caseSrv.get(`case`).update("description" -> description)
      _ <- importObservables(alert, `case`)
      _ <- alertCaseSrv.create(AlertCase(), alert, `case`)
      _ <- markAsRead(alert._id)
      _ <- auditSrv.alertToCase.merge(alert, c)
    } yield c

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
          .flatMap(duplicatedObservable => caseSrv.addObservable(`case`, duplicatedObservable))
          .recover { case _: CreateError => () } // ignore if case already contains observable
      }
      .map(_ => ())

  def cascadeRemove(alert: Alert with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      _ <- get(alert).observables.toIterator.toTry(observableSrv.cascadeRemove(_))
      _ = get(alert).remove()
      _ <- auditSrv.alert.delete(alert)
    } yield ()

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AlertSteps = new AlertSteps(raw)
}

@EntitySteps[Alert]
class AlertSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Alert](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex] = raw): AlertSteps = new AlertSteps(newRaw)

  def get(idOrSource: String): AlertSteps = idOrSource.split(';') match {
    case Array(tpe, source, sourceRef) => getBySourceId(tpe, source, sourceRef)
    case _                             => this.getByIds(idOrSource)
  }

  def getBySourceId(`type`: String, source: String, sourceRef: String): AlertSteps =
    this
      .has("type", `type`)
      .has("source", source)
      .has("sourceRef", sourceRef)

  def organisation: OrganisationSteps = new OrganisationSteps(raw.outTo[AlertOrganisation])

  def tags: TagSteps = new TagSteps(raw.outTo[AlertTag])

  def `case`: CaseSteps = new CaseSteps(raw.outTo[AlertCase])

  def removeTags(tags: Set[Tag with Entity]): Unit =
    if (tags.nonEmpty)
      this.outToE[AlertTag].filter(_.otherV().hasId(tags.map(_._id).toSeq: _*)).remove()

  def visible(implicit authContext: AuthContext): AlertSteps =
    this.filter(
      _.outTo[AlertOrganisation]
        .inTo[RoleOrganisation]
        .inTo[UserRole]
        .has("login", authContext.userId)
    )

  def can(permission: Permission)(implicit authContext: AuthContext): AlertSteps =
    this.filter(
      _.outTo[AlertOrganisation]
        .inTo[RoleOrganisation]
        .filter(_.outTo[RoleProfile].has("permissions", permission))
        .inTo[UserRole]
        .has("login", authContext.userId)
    )

  def alertUserOrganisation(
      permission: Permission
  )(implicit authContext: AuthContext): Traversal[(RichAlert, Organisation with Entity), (RichAlert, Organisation with Entity)] = {
    val alertLabel            = StepLabel[Vertex]()
    val organisationLabel     = StepLabel[Vertex]()
    val tagLabel              = StepLabel[JList[Vertex]]()
    val customFieldLabel      = StepLabel[JList[Path]]()
    val caseIdLabel           = StepLabel[JList[AnyRef]]()
    val caseTemplateNameLabel = StepLabel[JList[String]]()
    Traversal(
      raw
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
          val tags         = resultMap.getValue(tagLabel).asScala.map(_.as[Tag])
          val customFieldValues = resultMap
            .getValue(customFieldLabel)
            .asScala
            .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
            .map {
              case List(acf, cf) => RichCustomField(cf.as[CustomField], acf.as[AlertCustomField])
              case _             => throw InternalError("Not possible")
            }

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

  def customFields(name: String): CustomFieldValueSteps =
    new CustomFieldValueSteps(raw.outToE[AlertCustomField].filter(_.inV().has(Key("name") of name)))

  def customFields: CustomFieldValueSteps =
    new CustomFieldValueSteps(raw.outToE[AlertCustomField])

  def observables: ObservableSteps = new ObservableSteps(raw.outTo[AlertObservable])

  def richAlert: Traversal[RichAlert, RichAlert] =
    Traversal(
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
                case List(acf, cf) => RichCustomField(cf.as[CustomField], acf.as[AlertCustomField])
                case _             => throw InternalError("Not possible")
              }
            RichAlert(
              alert.as[Alert],
              onlyOneOf[String](organisation),
              tags.asScala.map(_.as[Tag]),
              customFieldValues,
              atMostOneOf[AnyRef](caseId).map(_.toString),
              atMostOneOf[String](caseTemplate)
            )
        }
    )
}
