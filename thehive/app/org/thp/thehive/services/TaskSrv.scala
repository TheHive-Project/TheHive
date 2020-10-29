package org.thp.thehive.services

import java.util
import java.util.Date

import javax.inject.{Inject, Named, Provider, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.models.{TaskStatus, _}
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.TaskOps._
import play.api.libs.json.{JsNull, JsObject, Json}

import scala.util.{Failure, Success, Try}

@Singleton
class TaskSrv @Inject() (
    @Named("with-thehive-schema") implicit val db: Database,
    caseSrvProvider: Provider[CaseSrv],
    logSrvProvider: Provider[LogSrv],
    auditSrv: AuditSrv
) extends VertexSrv[Task] {

  lazy val caseSrv: CaseSrv = caseSrvProvider.get
  lazy val logSrv: LogSrv   = logSrvProvider.get
  val caseTemplateTaskSrv   = new EdgeSrv[CaseTemplateTask, CaseTemplate, Task]
  val taskUserSrv           = new EdgeSrv[TaskUser, Task, User]
  val taskLogSrv            = new EdgeSrv[TaskLog, Task, Log]

  def create(e: Task, owner: Option[User with Entity])(implicit graph: Graph, authContext: AuthContext): Try[RichTask] =
    for {
      task <- createEntity(e)
      _    <- owner.map(taskUserSrv.create(TaskUser(), task, _)).flip
    } yield RichTask(task, owner)

  def isAvailableFor(taskId: EntityIdOrName)(implicit graph: Graph, authContext: AuthContext): Boolean =
    get(taskId).visible(authContext).exists

  def unassign(task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(task).unassign()
    auditSrv.task.update(task, Json.obj("assignee" -> JsNull))
  }

  def remove(task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    get(task).caseTemplate.headOption match {
      case None               => Try(get(task).remove())
      case Some(caseTemplate) =>
        auditSrv
          .caseTemplate
          .update(caseTemplate, JsObject.empty)
          .map(_ => get(task).remove())
    }

  def cascadeRemove(task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      task        <- getOrFail(task._id)
      logs        <- Try(get(task).logs.toSeq)
      _           <- logs.toTry(l => logSrv.cascadeRemove(l))
    } yield remove(task)

  override def update(
      traversal: Traversal.V[Task],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Task], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (taskSteps, updatedFields) =>
        for {
          t <- taskSteps.clone().getOrFail("Task")
          _ <- auditSrv.task.update(t, updatedFields)
        } yield ()
    }

  /**
    * Tries to update the status of a task with related fields
    * according the status value if empty
    * @param t the task to update
    * @param o the potential owner
    * @param s the status to set
    * @param graph db
    * @param authContext auth db
    * @return
    */
  def updateStatus(t: Task with Entity, o: User with Entity, s: TaskStatus.Value)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Task with Entity] = {
    def setStatus(): Try[Task with Entity] = get(t).update(_.status, s).getOrFail("")

    s match {
      case TaskStatus.Cancel | TaskStatus.Waiting => setStatus()
      case TaskStatus.Completed =>
        t.endDate.fold(get(t).update(_.status, s).update(_.endDate, Some(new Date())).getOrFail(""))(_ => setStatus())

      case TaskStatus.InProgress =>
        for {
          _       <- get(t).assignee.headOption.fold(assign(t, o))(_ => Success(()))
          updated <- t.startDate.fold(get(t).update(_.status, s).update(_.startDate, Some(new Date())).getOrFail(""))(_ => setStatus())
        } yield updated

      case _ => Failure(new Exception(s"Invalid TaskStatus $s for update"))
    }
  }

  def assign(task: Task with Entity, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(task).unassign()
    for {
      _ <- taskUserSrv.create(TaskUser(), task, user)
      _ <- auditSrv.task.update(task, Json.obj("assignee" -> user.login))
    } yield ()
  }
}

object TaskOps {
  implicit class TaskOpsDefs(traversal: Traversal.V[Task]) {

    def get(idOrName: EntityIdOrName): Traversal.V[Task] =
      idOrName.fold(traversal.getByIds(_), _ => traversal.limit(0))

    def visible(implicit authContext: AuthContext): Traversal.V[Task] =
      traversal.filter(_.organisations.current)

    def active: Traversal.V[Task] = traversal.filterNot(_.has(_.status, TaskStatus.Cancel))

    def can(permission: Permission)(implicit authContext: AuthContext): Traversal.V[Task] =
      if (authContext.permissions.contains(permission))
        traversal.filter(_.shares.filter(_.profile.has(_.permissions, permission)).organisation.current)
      else
        traversal.limit(0)

    def `case`: Traversal.V[Case] = traversal.in[ShareTask].out[ShareCase].dedup.v[Case]

    def caseTemplate: Traversal.V[CaseTemplate] = traversal.in[CaseTemplateTask].v[CaseTemplate]

    def caseTasks: Traversal.V[Task] = traversal.filter(_.inE[ShareTask]).v[Task]

    def caseTemplateTasks: Traversal.V[Task] = traversal.filter(_.inE[CaseTemplateTask]).v[Task]

    def logs: Traversal.V[Log] = traversal.out[TaskLog].v[Log]

    def assignee: Traversal.V[User] = traversal.out[TaskUser].v[User]

    def unassigned: Traversal.V[Task] = traversal.filterNot(_.outE[TaskUser])

    def organisations: Traversal.V[Organisation] = traversal.in[ShareTask].in[OrganisationShare].v[Organisation]
    def organisations(permission: Permission): Traversal.V[Organisation] =
      shares.filter(_.profile.has(_.permissions, permission)).organisation

    def origin: Traversal.V[Organisation] = shares.has(_.owner, true).organisation

    def assignableUsers(implicit authContext: AuthContext): Traversal.V[User] =
      organisations(Permissions.manageTask)
        .visible
        .users(Permissions.manageTask)
        .dedup

    def richTask: Traversal[RichTask, util.Map[String, Any], Converter[RichTask, util.Map[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.out[TaskUser].v[User].fold)
        )
        .domainMap {
          case (task, user) => RichTask(task, user.headOption)
        }

    def richTaskWithCustomRenderer[D, G, C <: Converter[D, G]](
        entityRenderer: Traversal.V[Task] => Traversal[D, G, C]
    ): Traversal[(RichTask, D), util.Map[String, Any], Converter[(RichTask, D), util.Map[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.assignee.fold)
            .by(entityRenderer)
        )
        .domainMap {
          case (task, user, renderedEntity) =>
            RichTask(task, user.headOption) -> renderedEntity
        }

    def unassign(): Unit = traversal.outE[TaskUser].remove()

    def shares: Traversal.V[Share] = traversal.in[ShareTask].v[Share]

    def share(implicit authContext: AuthContext): Traversal.V[Share] = share(authContext.organisation)

    def share(organisation: EntityIdOrName): Traversal.V[Share] =
      traversal.in[ShareTask].filter(_.in[OrganisationShare].v[Organisation].get(organisation)).v[Share]
  }
}
