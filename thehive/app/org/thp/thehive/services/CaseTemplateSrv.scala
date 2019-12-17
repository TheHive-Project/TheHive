package org.thp.thehive.services

import java.util.{List => JList}

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

import play.api.libs.json.{JsObject, Json}

import gremlin.scala.{__, By, Element, Graph, GremlinScala, Key, P, Vertex}
import javax.inject.Inject
import org.apache.tinkerpop.gremlin.process.traversal.Path
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.scalligraph.{CreateError, EntitySteps, InternalError, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._

class CaseTemplateSrv @Inject()(
    customFieldSrv: CustomFieldSrv,
    organisationSrv: OrganisationSrv,
    tagSrv: TagSrv,
    taskSrv: TaskSrv,
    auditSrv: AuditSrv
)(implicit db: Database)
    extends VertexSrv[CaseTemplate, CaseTemplateSteps] {

  val caseTemplateTagSrv          = new EdgeSrv[CaseTemplateTag, CaseTemplate, Tag]
  val caseTemplateCustomFieldSrv  = new EdgeSrv[CaseTemplateCustomField, CaseTemplate, CustomField]
  val caseTemplateOrganisationSrv = new EdgeSrv[CaseTemplateOrganisation, CaseTemplate, Organisation]
  val caseTemplateTaskSrv         = new EdgeSrv[CaseTemplateTask, CaseTemplate, Task]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): CaseTemplateSteps = new CaseTemplateSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): CaseTemplateSteps =
    if (db.isValidId(idOrName)) super.getByIds(idOrName)
    else initSteps.getByName(idOrName)

  def create(
      caseTemplate: CaseTemplate,
      organisation: Organisation with Entity,
      tagNames: Set[String],
      tasks: Seq[(Task, Option[User with Entity])],
      customFields: Seq[(String, Option[Any])]
  )(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichCaseTemplate] =
    if (organisationSrv.get(organisation).caseTemplates.has("name", P.eq[String](caseTemplate.name)).exists())
      Failure(CreateError(s"""The case template "${caseTemplate.name}" already exists"""))
    else
      for {
        createdCaseTemplate <- createEntity(caseTemplate)
        _                   <- caseTemplateOrganisationSrv.create(CaseTemplateOrganisation(), createdCaseTemplate, organisation)
        createdTasks        <- tasks.toTry { case (task, owner) => taskSrv.create(task, owner) }
        _                   <- createdTasks.toTry(rt => addTask(createdCaseTemplate, rt.task))
        tags                <- tagNames.toTry(tagSrv.getOrCreate)
        _                   <- tags.toTry(t => caseTemplateTagSrv.create(CaseTemplateTag(), createdCaseTemplate, t))
        cfs                 <- customFields.toTry { case (name, value) => createCustomField(createdCaseTemplate, name, value) }
        richCaseTemplate = RichCaseTemplate(createdCaseTemplate, organisation.name, tags, createdTasks, cfs)
        _ <- auditSrv.caseTemplate.create(createdCaseTemplate, richCaseTemplate.toJson)
      } yield richCaseTemplate

  def addTask(caseTemplate: CaseTemplate with Entity, task: Task with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      _ <- caseTemplateTaskSrv.create(CaseTemplateTask(), caseTemplate, task)
      _ <- auditSrv.taskInTemplate.create(task, caseTemplate, RichTask(task, None).toJson)
    } yield ()

  override def update(
      steps: CaseTemplateSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(CaseTemplateSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (templateSteps, updatedFields) =>
        templateSteps
          .newInstance()
          .getOrFail()
          .flatMap(auditSrv.caseTemplate.update(_, updatedFields))
    }

  def updateTagNames(caseTemplate: CaseTemplate with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    tags.toTry(tagSrv.getOrCreate).flatMap(t => updateTags(caseTemplate, t.toSet))

  def updateTags(caseTemplate: CaseTemplate with Entity, tags: Set[Tag with Entity])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val (tagsToAdd, tagsToRemove) = get(caseTemplate)
      .tags
      .toIterator
      .foldLeft((tags, Set.empty[Tag with Entity])) {
        case ((toAdd, toRemove), t) if toAdd.contains(t) => (toAdd - t, toRemove)
        case ((toAdd, toRemove), t)                      => (toAdd, toRemove + t)
      }
    for {
      _ <- tagsToAdd.toTry(caseTemplateTagSrv.create(CaseTemplateTag(), caseTemplate, _))
      _ = get(caseTemplate).removeTags(tagsToRemove)
      _ <- auditSrv.caseTemplate.update(caseTemplate, Json.obj("tags" -> tags.map(_.toString)))
    } yield ()
  }

  def addTags(caseTemplate: CaseTemplate with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val currentTags = get(caseTemplate)
      .tags
      .toList
      .map(_.toString)
      .toSet
    for {
      createdTags <- (tags -- currentTags).toTry(tagSrv.getOrCreate)
      _           <- createdTags.toTry(caseTemplateTagSrv.create(CaseTemplateTag(), caseTemplate, _))
      _           <- auditSrv.caseTemplate.update(caseTemplate, Json.obj("tags" -> (currentTags ++ tags)))
    } yield ()
  }

  def getCustomField(caseTemplate: CaseTemplate with Entity, customFieldName: String)(implicit graph: Graph): Option[RichCustomField] =
    get(caseTemplate).customFields(customFieldName).richCustomField.headOption()

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
      .toTry { case (cf, v) => setOrCreateCustomField(caseTemplate, cf.name, Some(v)) }
      .map(_ => ())
  }

  def setOrCreateCustomField(caseTemplate: CaseTemplate with Entity, customFieldName: String, value: Option[Any])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
    val cfv = get(caseTemplate).customFields(customFieldName)
    if (cfv.newInstance().exists())
      cfv.setValue(value)
    else
      createCustomField(caseTemplate, customFieldName, value).map(_ => ())
  }

  def createCustomField(
      caseTemplate: CaseTemplate with Entity,
      customFieldName: String,
      customFieldValue: Option[Any]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    for {
      cf   <- customFieldSrv.getOrFail(customFieldName)
      ccf  <- CustomFieldType.map(cf.`type`).setValue(CaseTemplateCustomField(), customFieldValue)
      ccfe <- caseTemplateCustomFieldSrv.create(ccf, caseTemplate, cf)
    } yield RichCustomField(cf, ccfe)
}

@EntitySteps[CaseTemplate]
class CaseTemplateSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[CaseTemplate](raw) {

  def get(idOrName: String): CaseTemplateSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): CaseTemplateSteps = newInstance(raw.has(Key("name") of name))

  override def newInstance(newRaw: GremlinScala[Vertex]): CaseTemplateSteps = new CaseTemplateSteps(newRaw)

  def visible(implicit authContext: AuthContext): CaseTemplateSteps =
    newInstance(raw.filter(_.outTo[CaseTemplateOrganisation].inTo[RoleOrganisation].inTo[UserRole].has(Key("login") of authContext.userId)))

  override def newInstance(): CaseTemplateSteps = new CaseTemplateSteps(raw.clone())

  def can(permission: Permission)(implicit authContext: AuthContext): CaseTemplateSteps =
    newInstance(
      raw.filter(
        _.outTo[CaseTemplateOrganisation]
          .inTo[RoleOrganisation]
          .filter(_.outTo[RoleProfile].has(Key("permissions") of permission))
          .inTo[UserRole]
          .has(Key("login") of authContext.userId)
      )
    )

  def richCaseTemplate: Traversal[RichCaseTemplate, RichCaseTemplate] =
    Traversal(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[CaseTemplateOrganisation].values[String]("name").fold))
            .and(By(__[Vertex].outTo[CaseTemplateTag].fold))
            .and(By(new TaskSteps(__[Vertex].outTo[CaseTemplateTask]).richTask.raw.fold))
            .and(By(__[Vertex].outToE[CaseTemplateCustomField].inV().path.fold.traversal))
        )
        .map {
          case (caseTemplate, organisation, tags, tasks, customFields) =>
            val customFieldValues = (customFields: JList[Path])
              .asScala
              .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
              .map {
                case List(ccf, cf) => RichCustomField(cf.as[CustomField], ccf.as[CaseCustomField])
                case _             => throw InternalError("Not possible")
              }
            RichCaseTemplate(
              caseTemplate.as[CaseTemplate],
              onlyOneOf[String](organisation),
              tags.asScala.map(_.as[Tag]),
              tasks.asScala,
              customFieldValues
            )
        }
    )

  def organisation = new OrganisationSteps(raw.outTo[CaseTemplateOrganisation])

  def tasks = new TaskSteps(raw.outTo[CaseTemplateTask])

  def tags: TagSteps = new TagSteps(raw.outTo[CaseTemplateTag])

  def removeTags(tags: Set[Tag with Entity]): Unit =
    if (tags.nonEmpty)
      this.outToE[CaseTemplateTag].filter(_.inV().hasId(tags.map(_._id).toSeq: _*)).remove()

  def customFields(name: String): CustomFieldValueSteps =
    new CustomFieldValueSteps(raw.outToE[CaseTemplateCustomField].filter(_.inV().has(Key("name") of name)))

  def customFields: CustomFieldValueSteps =
    new CustomFieldValueSteps(raw.outToE[CaseTemplateCustomField])
}
