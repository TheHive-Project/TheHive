package org.thp.thehive.services

import java.util.{List => JList}

import gremlin.scala.{__, By, Edge, Element, Graph, GremlinScala, Key, P, Vertex}
import javax.inject.Inject
import org.apache.tinkerpop.gremlin.process.traversal.Path
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity, ScalarSteps}
import org.thp.scalligraph.services._
import org.thp.scalligraph.{EntitySteps, InternalError, RichSeq}
import org.thp.thehive.models._

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

  override def get(id: String)(implicit graph: Graph): CaseTemplateSteps =
    if (db.isValidId(id)) super.get(id)
    else initSteps.getByName(id)

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

    val createdCaseTemplate = create(caseTemplate)
    caseTemplateOrganisationSrv.create(CaseTemplateOrganisation(), createdCaseTemplate, organisation)
    val createdTasks = tasks.map { t =>
      val ct = taskSrv.create(t, createdCaseTemplate)
      caseTemplateTaskSrv.create(CaseTemplateTask(), createdCaseTemplate, ct)
      ct
    }
    for {
      tags <- tagNames.toTry(t => tagSrv.getOrCreate(t))
      _ = tags.foreach(t => caseTemplateTagSrv.create(CaseTemplateTag(), createdCaseTemplate, t))
      cfs <- customFields
        .toTry {
          case (name, Some(value)) =>
            for {
              customField <- customFieldSrv.getOrFail(name)
              ctcf        <- customField.`type`.setValue(CaseTemplateCustomField(), value)
              caseTemplateCustomField = caseTemplateCustomFieldSrv.create(ctcf, createdCaseTemplate, customField)
            } yield CustomFieldWithValue(customField, caseTemplateCustomField)
          case (name, None) =>
            customFieldSrv.getOrFail(name).map { customField =>
              val caseTemplateCustomField = caseTemplateCustomFieldSrv.create(CaseTemplateCustomField(), createdCaseTemplate, customField)
              CustomFieldWithValue(customField, caseTemplateCustomField)
            }
        }
      _ <- auditSrv.createCaseTemplate(createdCaseTemplate)
    } yield RichCaseTemplate(createdCaseTemplate, organisation.name, tags, createdTasks, cfs)
  }

  def updateTags(caseTemplate: CaseTemplate with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val (tagsToAdd, tagsToRemove) = get(caseTemplate)
      .tags
      .toIterator
      .foldLeft((tags, Set.empty[String])) {
        case ((toAdd, toRemove), t) if toAdd.contains(t.name) => (toAdd - t.name, toRemove)
        case ((toAdd, toRemove), t)                           => (toAdd, toRemove + t.name)
      }
    tagsToAdd
      .toTry(tn => tagSrv.getOrCreate(tn).map(t => caseTemplateTagSrv.create(CaseTemplateTag(), caseTemplate, t)))
      .map(_ => get(caseTemplate).removeTags(tagsToRemove))
  }

  def addTags(caseTemplate: CaseTemplate with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val currentTags = get(caseTemplate)
      .tags
      .toList
      .map(_.name)
      .toSet
    (tags.toSet -- currentTags)
      .toTry(tn => tagSrv.getOrCreate(tn).map(t => caseTemplateTagSrv.create(CaseTemplateTag(), caseTemplate, t)))
      .map(_ => ())
  }

//  def addTask(caseTemplate: CaseTemplate with Entity, task: Task)(implicit graph: Graph, authContext: AuthContext): Try[Task with Entity] = {
//    for {
//      createdTask <- taskSrv.create
//    } auditSrv.addTaskToCaseTemplate(caseTemplate, task)
//    caseTemplateTaskSrv.create(CaseTemplateTask(), caseTemplate, task)
//    ()
//  }
}

@EntitySteps[CaseTemplate]
class CaseTemplateSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph)
    extends BaseVertexSteps[CaseTemplate, CaseTemplateSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): CaseTemplateSteps = new CaseTemplateSteps(raw)

  override def get(id: String): CaseTemplateSteps =
    if (db.isValidId(id)) getById(id)
    else getByName(id)

  def getById(id: String): CaseTemplateSteps = newInstance(raw.hasId(id))

  def getByName(name: String): CaseTemplateSteps = newInstance(raw.has(Key("name") of name))

  def visible(implicit authContext: AuthContext): CaseTemplateSteps =
    newInstance(raw.filter(_.outTo[CaseTemplateOrganisation].inTo[RoleOrganisation].inTo[UserRole].has(Key("login") of authContext.userId)))

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
