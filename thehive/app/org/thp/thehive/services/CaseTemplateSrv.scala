package org.thp.thehive.services

import java.util.{List => JList}

import gremlin.scala.{__, By, Edge, Element, Graph, GremlinScala, Key, P, Vertex}
import javax.inject.Inject
import org.apache.tinkerpop.gremlin.process.traversal.Path
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity, ScalarSteps}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.{EntitySteps, InternalError, RichSeq}
import org.thp.thehive.models._
import play.api.libs.json.{JsObject, Json}

import scala.collection.JavaConverters._
import scala.util.Try

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
      tagNames: Seq[String],
      tasks: Seq[Task],
      customFields: Seq[(String, Option[Any])]
  )(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichCaseTemplate] = {

    def createCustomFields(caseTemplate: CaseTemplate with Entity, customFields: Seq[(String, Option[Any])]): Try[Seq[CustomFieldWithValue]] =
      customFields
        .toTry {
          case (name, Some(value)) =>
            for {
              cf                      <- customFieldSrv.getOrFail(name)
              ccf                     <- cf.`type`.setValue(CaseTemplateCustomField(), value)
              caseTemplateCustomField <- caseTemplateCustomFieldSrv.create(ccf, caseTemplate, cf)
            } yield CustomFieldWithValue(cf, caseTemplateCustomField)
          case (name, _) =>
            for {
              cf                      <- customFieldSrv.getOrFail(name)
              caseTemplateCustomField <- caseTemplateCustomFieldSrv.create(CaseTemplateCustomField(), caseTemplate, cf)
            } yield CustomFieldWithValue(cf, caseTemplateCustomField)
        }

    for {
      createdCaseTemplate <- createEntity(caseTemplate)
      _                   <- caseTemplateOrganisationSrv.create(CaseTemplateOrganisation(), createdCaseTemplate, organisation)
      _                   <- createCustomFields(createdCaseTemplate, customFields)
      createdTasks        <- tasks.toTry(taskSrv.create)
      _                   <- createdTasks.toTry(addTask(createdCaseTemplate, _))
      tags                <- tagNames.toTry(t => tagSrv.getOrCreate(t))
      _                   <- tags.toTry(t => caseTemplateTagSrv.create(CaseTemplateTag(), createdCaseTemplate, t))
      cfs                 <- createCustomFields(createdCaseTemplate, customFields)
      _                   <- auditSrv.caseTemplate.create(createdCaseTemplate)
    } yield RichCaseTemplate(createdCaseTemplate, organisation.name, tags, createdTasks, cfs)
  }

  def addTask(caseTemplate: CaseTemplate with Entity, task: Task with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      _ <- caseTemplateTaskSrv.create(CaseTemplateTask(), caseTemplate, task)
      _ <- auditSrv.taskInTemplate.create(task, caseTemplate)
    } yield ()

  override def update(
      steps: CaseTemplateSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(CaseTemplateSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (templateSteps, updatedFields) =>
        templateSteps
          .clone()
          .getOrFail()
          .flatMap(auditSrv.caseTemplate.update(_, updatedFields))
    }

  def updateTags(caseTemplate: CaseTemplate with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val (tagsToAdd, tagsToRemove) = get(caseTemplate)
      .tags
      .toIterator
      .foldLeft((tags, Set.empty[String])) {
        case ((toAdd, toRemove), t) if toAdd.contains(t.name) => (toAdd - t.name, toRemove)
        case ((toAdd, toRemove), t)                           => (toAdd, toRemove + t.name)
      }
    for {
      createdTags <- tagsToAdd.toTry(tagSrv.getOrCreate(_))
      _           <- createdTags.toTry(caseTemplateTagSrv.create(CaseTemplateTag(), caseTemplate, _))
      _ = get(caseTemplate).removeTags(tagsToRemove)
      _ <- auditSrv.caseTemplate.update(caseTemplate, Json.obj("tags" -> tags))
    } yield ()
  }

  def addTags(caseTemplate: CaseTemplate with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val currentTags = get(caseTemplate)
      .tags
      .toList
      .map(_.name)
      .toSet
    for {
      createdTags <- (tags -- currentTags).toTry(tagSrv.getOrCreate(_))
      _           <- createdTags.toTry(caseTemplateTagSrv.create(CaseTemplateTag(), caseTemplate, _))
      _           <- auditSrv.caseTemplate.update(caseTemplate, Json.obj("tags" -> (currentTags ++ tags)))
    } yield ()
  }

}

@EntitySteps[CaseTemplate]
class CaseTemplateSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph)
    extends BaseVertexSteps[CaseTemplate, CaseTemplateSteps](raw) {

  def get(idOrName: String): CaseTemplateSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): CaseTemplateSteps = newInstance(raw.has(Key("name") of name))

  def visible(implicit authContext: AuthContext): CaseTemplateSteps =
    newInstance(raw.filter(_.outTo[CaseTemplateOrganisation].inTo[RoleOrganisation].inTo[UserRole].has(Key("login") of authContext.userId)))

  override def newInstance(raw: GremlinScala[Vertex]): CaseTemplateSteps = new CaseTemplateSteps(raw)

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

  def customFields: ScalarSteps[CustomFieldWithValue] =
    ScalarSteps(
      raw
        .outToE[CaseTemplateCustomField]
        .inV()
        .path
        .map(path => CustomFieldWithValue(path.get[Vertex](2).as[CustomField], path.get[Edge](1).as[CaseTemplateCustomField]))
    )

  def richCaseTemplate: ScalarSteps[RichCaseTemplate] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[CaseTemplateOrganisation].values[String]("name").fold))
            .and(By(__[Vertex].outTo[CaseTemplateTag].fold))
            .and(By(__[Vertex].outTo[CaseTemplateTask].fold))
            .and(By(__[Vertex].outToE[CaseTemplateCustomField].inV().path.fold.traversal))
        )
        .map {
          case (caseTemplate, organisation, tags, tasks, customFields) =>
            val customFieldValues = (customFields: JList[Path])
              .asScala
              .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
              .map {
                case List(ccf, cf) => CustomFieldWithValue(cf.as[CustomField], ccf.as[CaseCustomField])
                case _             => throw InternalError("Not possible")
              }
            RichCaseTemplate(
              caseTemplate.as[CaseTemplate],
              onlyOneOf[String](organisation),
              tags.asScala.map(_.as[Tag]).toSeq,
              tasks.asScala.map(_.as[Task]).toSeq,
              customFieldValues.toSeq
            )
        }
    )

  def tasks = new TaskSteps(raw.outTo[CaseTemplateTask])

  def tags: TagSteps = new TagSteps(raw.outTo[CaseTemplateTag])

  def removeTags(tagNames: Set[String]): Unit = {
    raw.outToE[CaseTemplateTag].where(_.otherV().has(Key[String]("name"), P.within(tagNames))).drop().iterate()
    ()
  }
}
