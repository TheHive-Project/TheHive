package org.thp.thehive.services

import akka.actor.ActorRef
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{Database, Entity}
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
import play.api.libs.json.{JsObject, Json}

import java.util.{Map => JMap}
import javax.inject.{Inject, Named}
import scala.util.{Failure, Success, Try}

class CaseTemplateSrv @Inject() (
    customFieldSrv: CustomFieldSrv,
    organisationSrv: OrganisationSrv,
    tagSrv: TagSrv,
    taskSrv: TaskSrv,
    auditSrv: AuditSrv,
    @Named("integrity-check-actor") integrityCheckActor: ActorRef
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
        cfs                 <- customFields.zipWithIndex.toTry { case ((name, value), order) => createCustomField(createdCaseTemplate, name, value, Some(order + 1)) }
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
      tagsToAdd    <- (tags -- caseTemplate.tags).toTry(tagSrv.getOrCreate)
      tagsToRemove <- (caseTemplate.tags.toSet -- tags).toTry(tagSrv.getOrCreate)
      _            <- tagsToAdd.toTry(caseTemplateTagSrv.create(CaseTemplateTag(), caseTemplate, _))
      _ = if (tags.nonEmpty) get(caseTemplate).outE[CaseTemplateTag].filter(_.otherV.hasId(tagsToRemove.map(_._id): _*)).remove()
      _ <- get(caseTemplate).update(_.tags, tags.toSeq).getOrFail("CaseTemplate")
      _ <- auditSrv.caseTemplate.update(caseTemplate, Json.obj("tags" -> tags))
    } yield (tagsToAdd, tagsToRemove)

  def addTags(caseTemplate: CaseTemplate with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    updateTags(caseTemplate, tags ++ caseTemplate.tags).map(_ => ())

  def getCustomField(caseTemplate: CaseTemplate with Entity, customFieldName: String)(implicit graph: Graph): Option[RichCustomField] =
    get(caseTemplate).customFields(customFieldName).richCustomField.headOption

  def updateCustomField(
      caseTemplate: CaseTemplate with Entity,
      customFieldValues: Seq[(CustomField, Any)]
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val customFieldNames = customFieldValues.map(_._1.name)
    get(caseTemplate)
      .customFields
      .richCustomField
      .toIterator
      .filterNot(rcf => customFieldNames.contains(rcf.name))
      .foreach(rcf => get(caseTemplate).customFields(rcf.name).remove())
    customFieldValues
      .zipWithIndex
      .toTry { case ((cf, v), o) => setOrCreateCustomField(caseTemplate, cf.name, Some(v), Some(o + 1)) }
      .map(_ => ())
  }

  def setOrCreateCustomField(caseTemplate: CaseTemplate with Entity, customFieldName: String, value: Option[Any], order: Option[Int])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
    val cfv = get(caseTemplate).customFields(customFieldName)
    if (cfv.clone().exists)
      cfv.setValue(value)
    else
      createCustomField(caseTemplate, customFieldName, value, order).map(_ => ())
  }

  def createCustomField(
      caseTemplate: CaseTemplate with Entity,
      customFieldName: String,
      customFieldValue: Option[Any],
      order: Option[Int]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    for {
      cf   <- customFieldSrv.getOrFail(EntityIdOrName(customFieldName))
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

    def customFields(name: String): Traversal.E[CaseTemplateCustomField] =
      traversal.outE[CaseTemplateCustomField].filter(_.inV.v[CustomField].has(_.name, name))

    def customFields: Traversal.E[CaseTemplateCustomField] =
      traversal.outE[CaseTemplateCustomField]
  }

  implicit class CaseTemplateCustomFieldsOpsDefs(traversal: Traversal.E[CaseTemplateCustomField]) extends CustomFieldValueOpsDefs(traversal)
}

class CaseTemplateIntegrityCheckOps @Inject() (
    val db: Database,
    val service: CaseTemplateSrv,
    organisationSrv: OrganisationSrv
) extends IntegrityCheckOps[CaseTemplate] {
  override def duplicateEntities: Seq[Seq[CaseTemplate with Entity]] =
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
}
