package org.thp.thehive.services

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PredicateOps.PredicateOpsDefs
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Graph, StepLabel, Traversal}
import org.thp.scalligraph.{CreateError, EntityIdOrName, EntityName, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.CustomFieldOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.UserOps._
import play.api.libs.json.{JsObject, JsValue, Json}

import java.util.{Date, Map => JMap}
import scala.util.{Failure, Success, Try}

class CaseTemplateSrv(
    customFieldSrv: CustomFieldSrv,
    organisationSrv: OrganisationSrv,
    tagSrv: TagSrv,
    taskSrv: TaskSrv,
    auditSrv: AuditSrv,
    integrityCheckActor: => ActorRef @@ IntegrityCheckTag
) extends VertexSrv[CaseTemplate] {

  val caseTemplateTagSrv          = new EdgeSrv[CaseTemplateTag, CaseTemplate, Tag]
  val caseTemplateCustomFieldSrv  = new EdgeSrv[CaseTemplateCustomField, CaseTemplate, CustomField]
  val caseTemplateOrganisationSrv = new EdgeSrv[CaseTemplateOrganisation, CaseTemplate, Organisation]
  val caseTemplateTaskSrv         = new EdgeSrv[CaseTemplateTask, CaseTemplate, Task]

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[CaseTemplate] =
    startTraversal.getByName(name)

  override def createEntity(e: CaseTemplate)(implicit graph: Graph, authContext: AuthContext): Try[CaseTemplate with Entity] = {
    integrityCheckActor ! EntityAdded("CaseTemplate")
    super.createEntity(e)
  }

  def create(
      caseTemplate: CaseTemplate,
      organisation: Organisation with Entity,
      tasks: Seq[Task],
      customFields: Seq[(String, Option[Any])]
  )(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichCaseTemplate] =
    if (organisationSrv.get(organisation).caseTemplates.get(EntityName(caseTemplate.name)).exists)
      Failure(CreateError(s"""The case template "${caseTemplate.name}" already exists"""))
    else
      for {
        createdCaseTemplate <- createEntity(caseTemplate)
        _                   <- caseTemplateOrganisationSrv.create(CaseTemplateOrganisation(), createdCaseTemplate, organisation)
        createdTasks        <- tasks.toTry(createTask(createdCaseTemplate, _))
        _                   <- caseTemplate.tags.toTry(tagSrv.getOrCreate(_).flatMap(t => caseTemplateTagSrv.create(CaseTemplateTag(), createdCaseTemplate, t)))
        cfs <- customFields.zipWithIndex.toTry {
          case ((name, value), order) => createCustomField(createdCaseTemplate, EntityIdOrName(name), value, Some(order + 1))
        }
        richCaseTemplate = RichCaseTemplate(createdCaseTemplate, organisation.name, createdTasks, cfs)
        _ <- auditSrv.caseTemplate.create(createdCaseTemplate, richCaseTemplate.toJson)
      } yield richCaseTemplate

  def createTask(caseTemplate: CaseTemplate with Entity, task: Task)(implicit graph: Graph, authContext: AuthContext): Try[RichTask] =
    for {
      assignee <- task.assignee.map(u => organisationSrv.current.users(Permissions.manageTask).getByName(u).getOrFail("User")).flip
      richTask <- taskSrv.create(task.copy(relatedId = caseTemplate._id, organisationIds = Set(organisationSrv.currentId)), assignee)
      _        <- caseTemplateTaskSrv.create(CaseTemplateTask(), caseTemplate, richTask.task)
      _        <- auditSrv.taskInTemplate.create(richTask.task, caseTemplate, richTask.toJson)
    } yield richTask

  override def update(
      traversal: Traversal.V[CaseTemplate],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[CaseTemplate], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (templateSteps, updatedFields) =>
        templateSteps
          .clone()
          .getOrFail("CaseTemplate")
          .flatMap(auditSrv.caseTemplate.update(_, updatedFields))
    }
  def updateTags(caseTemplate: CaseTemplate with Entity, tags: Set[String])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[(Seq[Tag with Entity], Seq[Tag with Entity])] =
    for {
      tagsToAdd <- (tags -- caseTemplate.tags).toTry(tagSrv.getOrCreate)
      tagsToRemove = get(caseTemplate).tags.toSeq.filterNot(t => tags.contains(t.toString))
      _ <- tagsToAdd.toTry(caseTemplateTagSrv.create(CaseTemplateTag(), caseTemplate, _))
      _ = if (tags.nonEmpty) get(caseTemplate).outE[CaseTemplateTag].filter(_.otherV.hasId(tagsToRemove.map(_._id): _*)).remove()
      _ <- get(caseTemplate).update(_.tags, tags.toSeq).getOrFail("CaseTemplate")
      _ <- auditSrv.caseTemplate.update(caseTemplate, Json.obj("tags" -> tags))
    } yield (tagsToAdd, tagsToRemove)

  def addTags(caseTemplate: CaseTemplate with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    updateTags(caseTemplate, tags ++ caseTemplate.tags).map(_ => ())

  def getCustomField(caseTemplate: CaseTemplate with Entity, customFieldIdOrName: EntityIdOrName)(implicit graph: Graph): Option[RichCustomField] =
    get(caseTemplate).customFields(customFieldIdOrName).richCustomField.headOption

  def updateCustomField(
      caseTemplate: CaseTemplate with Entity,
      customFieldValues: Seq[(CustomField, Any, Option[Int])]
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val customFieldNames = customFieldValues.map(_._1.name)
    get(caseTemplate)
      .customFields
      .richCustomField
      .toIterator
      .filterNot(rcf => customFieldNames.contains(rcf.name))
      .foreach(rcf => get(caseTemplate).customFields(EntityName(rcf.name)).remove())
    customFieldValues
      .toTry { case (cf, v, o) => setOrCreateCustomField(caseTemplate, EntityName(cf.name), Some(v), o) }
      .map(_ => ())
  }

  def setOrCreateCustomField(caseTemplate: CaseTemplate with Entity, customFieldIdOrName: EntityIdOrName, value: Option[Any], order: Option[Int])(
      implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
    val cfv = get(caseTemplate).customFields(customFieldIdOrName)
    if (cfv.clone().exists)
      cfv.setValue(value)
    else
      createCustomField(caseTemplate, customFieldIdOrName, value, order).map(_ => ())
  }

  def createCustomField(
      caseTemplate: CaseTemplate with Entity,
      customFieldIdOrName: EntityIdOrName,
      customFieldValue: Option[Any],
      order: Option[Int]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    for {
      cf   <- customFieldSrv.getOrFail(customFieldIdOrName)
      ccf  <- CustomFieldType.map(cf.`type`).setValue(CaseTemplateCustomField(order = order), customFieldValue)
      ccfe <- caseTemplateCustomFieldSrv.create(ccf, caseTemplate, cf)
    } yield RichCustomField(cf, ccfe)
}

object CaseTemplateOps {
  implicit class CaseTemplateOpsDefs(traversal: Traversal.V[CaseTemplate]) {

    def get(idOrName: EntityIdOrName): Traversal.V[CaseTemplate] =
      idOrName.fold(traversal.getByIds(_), getByName)

    def getByName(name: String): Traversal.V[CaseTemplate] = traversal.has(_.name, name)

    def visible(implicit authContext: AuthContext): Traversal.V[CaseTemplate] =
      traversal.filter(_.organisation.current)

    def can(permission: Permission)(implicit authContext: AuthContext): Traversal.V[CaseTemplate] =
      if (authContext.permissions.contains(permission))
        traversal.filter(_.organisation.current)
      else
        traversal.empty

    def richCaseTemplate: Traversal[RichCaseTemplate, JMap[String, Any], Converter[RichCaseTemplate, JMap[String, Any]]] = {
      val caseTemplateCustomFieldLabel = StepLabel.e[CaseTemplateCustomField]
      val customFieldLabel             = StepLabel.v[CustomField]
      traversal
        .project(
          _.by
            .by(_.organisation.value(_.name))
            .by(_.tasks.richTaskWithoutActionRequired.fold)
            .by(
              _.outE[CaseTemplateCustomField]
                .as(caseTemplateCustomFieldLabel)
                .inV
                .v[CustomField]
                .as(customFieldLabel)
                .select((caseTemplateCustomFieldLabel, customFieldLabel))
                .fold
            )
        )
        .domainMap {
          case (caseTemplate, organisation, tasks, customFields) =>
            RichCaseTemplate(
              caseTemplate,
              organisation,
              tasks,
              customFields.map(cf => RichCustomField(cf._2, cf._1))
            )
        }
    }

    def organisation: Traversal.V[Organisation] = traversal.out[CaseTemplateOrganisation].v[Organisation]

    def tasks: Traversal.V[Task] = traversal.out[CaseTemplateTask].v[Task]

    def tags: Traversal.V[Tag] = traversal.out[CaseTemplateTag].v[Tag]

    def customFields(idOrName: EntityIdOrName): Traversal.E[CaseTemplateCustomField] =
      idOrName
        .fold(
          id => customFields.filter(_.inV.getByIds(id)),
          name => customFields.filter(_.inV.v[CustomField].has(_.name, name))
        )

    def customFields: Traversal.E[CaseTemplateCustomField] =
      traversal.outE[CaseTemplateCustomField]

    def customFieldJsonValue(customFieldSrv: CustomFieldSrv, customField: EntityIdOrName): Traversal.Domain[JsValue] =
      customFieldSrv
        .get(customField)(traversal.graph)
        .value(_.`type`)
        .headOption
        .map(t => CustomFieldType.map(t).getJsonValue(traversal.customFields(customField)))
        .getOrElse(traversal.empty.castDomain)

    def customFieldFilter(customFieldSrv: CustomFieldSrv, customField: EntityIdOrName, predicate: P[JsValue]): Traversal.V[CaseTemplate] =
      customFieldSrv
        .get(customField)(traversal.graph)
        .value(_.`type`)
        .headOption
        .map {
          case CustomFieldType.boolean =>
            traversal.filter(_.customFields.has(_.booleanValue, predicate.mapValue(_.as[Boolean])).inV.v[CustomField].get(customField))
          case CustomFieldType.date =>
            traversal.filter(_.customFields.has(_.dateValue, predicate.mapValue(_.as[Date])).inV.v[CustomField].get(customField))
          case CustomFieldType.float =>
            traversal.filter(_.customFields.has(_.floatValue, predicate.mapValue(_.as[Double])).inV.v[CustomField].get(customField))
          case CustomFieldType.integer =>
            traversal.filter(_.customFields.has(_.integerValue, predicate.mapValue(_.as[Int])).inV.v[CustomField].get(customField))
          case CustomFieldType.string =>
            traversal.filter(_.customFields.has(_.stringValue, predicate.mapValue(_.as[String])).inV.v[CustomField].get(customField))
        }
        .getOrElse(traversal.empty)

    def hasCustomField(customFieldSrv: CustomFieldSrv, customField: EntityIdOrName): Traversal.V[CaseTemplate] =
      customFieldSrv
        .get(customField)(traversal.graph)
        .value(_.`type`)
        .headOption
        .map {
          case CustomFieldType.boolean => traversal.filter(_.customFields.has(_.booleanValue).inV.v[CustomField].get(customField))
          case CustomFieldType.date    => traversal.filter(_.customFields.has(_.dateValue).inV.v[CustomField].get(customField))
          case CustomFieldType.float   => traversal.filter(_.customFields.has(_.floatValue).inV.v[CustomField].get(customField))
          case CustomFieldType.integer => traversal.filter(_.customFields.has(_.integerValue).inV.v[CustomField].get(customField))
          case CustomFieldType.string  => traversal.filter(_.customFields.has(_.stringValue).inV.v[CustomField].get(customField))
        }
        .getOrElse(traversal.empty)

    def hasNotCustomField(customFieldSrv: CustomFieldSrv, customField: EntityIdOrName): Traversal.V[CaseTemplate] =
      customFieldSrv
        .get(customField)(traversal.graph)
        .value(_.`type`)
        .headOption
        .map {
          case CustomFieldType.boolean => traversal.filterNot(_.customFields.has(_.booleanValue).inV.v[CustomField].get(customField))
          case CustomFieldType.date    => traversal.filterNot(_.customFields.has(_.dateValue).inV.v[CustomField].get(customField))
          case CustomFieldType.float   => traversal.filterNot(_.customFields.has(_.floatValue).inV.v[CustomField].get(customField))
          case CustomFieldType.integer => traversal.filterNot(_.customFields.has(_.integerValue).inV.v[CustomField].get(customField))
          case CustomFieldType.string  => traversal.filterNot(_.customFields.has(_.stringValue).inV.v[CustomField].get(customField))
        }
        .getOrElse(traversal.empty)
  }

  implicit class CaseTemplateCustomFieldsOpsDefs(traversal: Traversal.E[CaseTemplateCustomField]) extends CustomFieldValueOpsDefs(traversal)
}

class CaseTemplateIntegrityCheckOps(
    val db: Database,
    val service: CaseTemplateSrv,
    organisationSrv: OrganisationSrv
) extends IntegrityCheckOps[CaseTemplate] {
  override def findDuplicates: Seq[Seq[CaseTemplate with Entity]] =
    db.roTransaction { implicit graph =>
      organisationSrv
        .startTraversal
        .flatMap(
          _.in[CaseTemplateOrganisation]
            .v[Organisation]
            .group(_.byValue(_.name), _.by(_._id.fold))
            .unfold
            .selectValues
            .where(_.localCount.is(P.gt(1)))
            .traversal
        )
        .domainMap(ids => service.getByIds(ids: _*).toSeq)
        .toSeq
    }

  override def resolve(entities: Seq[CaseTemplate with Entity])(implicit graph: Graph): Try[Unit] =
    entities match {
      case head :: tail =>
        tail.foreach(copyEdge(_, head, e => e.label() == "CaseCaseTemplate" || e.label() == "AlertCaseTemplate"))
        service.getByIds(tail.map(_._id): _*).remove()
        Success(())
      case _ => Success(())
    }

  override def globalCheck(): Map[String, Long] =
    db.tryTransaction { implicit graph =>
      Try {
        val orphanIds = service.startTraversal.filterNot(_.organisation)._id.toSeq
        if (orphanIds.nonEmpty) {
          logger.warn(s"Found ${orphanIds.length} caseTemplate orphan(s) (${orphanIds.mkString(",")})")
          service.getByIds(orphanIds: _*).remove()
        }
        Map("orphans" -> orphanIds.size.toLong)
      }
    }.getOrElse(Map("globalFailure" -> 1L))
}
