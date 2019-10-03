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
import org.thp.scalligraph.services.{EdgeSrv, _}
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.scalligraph.{CreateError, EntitySteps, InternalError, RichJMap, RichOptionTry, RichSeq}
import org.thp.thehive.models._
import shapeless.HNil

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
      caseTemplate: Option[RichCaseTemplate]
  )(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichAlert] = {

    def createCustomFields(alert: Alert with Entity): Try[Seq[CustomFieldWithValue]] =
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

    val alertAlreadyExist = organisationSrv.get(organisation).alerts.getBySourceId(alert.`type`, alert.source, alert.sourceRef).getCount
    if (alertAlreadyExist > 0)
      Failure(CreateError(s"Alert ${alert.`type`}:${alert.source}:${alert.sourceRef} already exist in organisation ${organisation.name}"))
    else
      for {
        createdAlert <- createEntity(alert)
        _            <- alertOrganisationSrv.create(AlertOrganisation(), createdAlert, organisation)
        _            <- caseTemplate.map(ct => alertCaseTemplateSrv.create(AlertCaseTemplate(), createdAlert, ct.caseTemplate)).flip
        tags         <- tagNames.toTry(tagSrv.getOrCreate)
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
          .newInstance()
          .getOrFail()
          .flatMap(auditSrv.alert.update(_, updatedFields))
    }

  def updateTags(alert: Alert with Entity, tags: Set[Tag with Entity])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val (tagsToAdd, tagsToRemove) = get(alert)
      .tags
      .toIterator
      .foldLeft((tags, Set.empty[Tag with Entity])) {
        case ((toAdd, toRemove), t) if toAdd.contains(t) => (toAdd - t, toRemove) // TODO need check if contains method works
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

  def addObservable(alert: Alert with Entity, observable: Observable with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
    val alreadyExistInThatCase = observableSrv
      .get(observable)
      .similar
      .alert
      .hasId(alert._id)
      .exists()
    if (alreadyExistInThatCase)
      Failure(CreateError("Observable already exist"))
    else
      for {
        _ <- alertObservableSrv.create(AlertObservable(), alert, observable)
        _ <- auditSrv.observableInAlert.create(observable, alert)
      } yield ()
  }

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

  def getCustomField(alert: Alert with Entity, customFieldName: String)(implicit graph: Graph): Option[CustomFieldWithValue] =
    get(alert).customFieldsValue.toIterator.find(_.name == customFieldName)

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

      createdCase <- caseSrv.create(case0, user, organisation, alert.tags.toSet, customField, caseTemplate, Nil)
      _           <- importObservables(alert.alert, createdCase.`case`)
      _           <- alertCaseSrv.create(AlertCase(), alert.alert, createdCase.`case`)
      _           <- markAsRead(alert._id)
      _           <- auditSrv.`case`.create(createdCase.`case`, Some(Json.obj("operation" -> "createFromAlert")))
    } yield createdCase

  def mergeInCase(alertId: String, caseId: String)(implicit graph: Graph, authContext: AuthContext): Try[Case with Entity] =
    for {
      alert <- getOrFail(alertId)
      case0 <- caseSrv.getOrFail(caseId)
      description = case0.description + s"\n  \n#### Merged with alert #${alert.sourceRef} ${alert.title}\n\n${alert.description.trim}"
      c <- caseSrv.get(caseId).update("description" -> description)
      _ <- importObservables(alert, case0)
      _ <- alertCaseSrv.create(AlertCase(), alert, case0)
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
          .flatMap(duplicatedObservable => caseSrv.addObservable(`case`, duplicatedObservable.observable))
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
}

@EntitySteps[Alert]
class AlertSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Alert](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex] = raw): AlertSteps = new AlertSteps(newRaw)

  def get(idOrSource: String): AlertSteps = idOrSource.split(';') match {
    case Array(tpe, source, sourceRef) => getBySourceId(tpe, source, sourceRef)
    case _                             => this.getByIds(idOrSource)
  }

  def getBySourceId(`type`: String, source: String, sourceRef: String): AlertSteps =
    newInstance(
      raw
        .has(Key("type") of `type`)
        .has(Key("source") of source)
        .has(Key("sourceRef") of sourceRef)
    )

  def organisation: OrganisationSteps = new OrganisationSteps(raw.outTo[AlertOrganisation])

  def tags: TagSteps = new TagSteps(raw.outTo[AlertTag])

  def `case`: CaseSteps = new CaseSteps(raw.outTo[AlertCase])

  def removeTags(tags: Set[Tag with Entity]): Unit =
    this.outToE[AlertTag].filter(_.otherV().hasId(tags.map(_._id).toSeq: _*)).remove()

  def visible(implicit authContext: AuthContext): AlertSteps =
    this.filter(
      _.outTo[AlertOrganisation]
        .inTo[RoleOrganisation]
        .inTo[UserRole]
        .has(Key("login"), P.eq(authContext.userId))
    )

  def can(permission: Permission)(implicit authContext: AuthContext): AlertSteps =
    this.filter(
      _.outTo[AlertOrganisation]
        .inTo[RoleOrganisation]
        .filter(_.outTo[RoleProfile].has(Key("permissions"), P.eq(permission)))
        .inTo[UserRole]
        .has(Key("login"), P.eq(authContext.userId))
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

  def customFields: CustomFieldSteps = new CustomFieldSteps(raw.outTo[AlertCustomField])

  def customFieldsValue: Traversal[CustomFieldWithValue, CustomFieldWithValue] =
    new Traversal(
      raw
        .outToE[AlertCustomField]
        .inV()
        .path
        .map(path => CustomFieldWithValue(path.get[Vertex](2).as[CustomField], path.get[Edge](1).as[AlertCustomField])),
      UniMapping.identity
    )

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
