package org.thp.thehive.services

import scala.util.Try

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity, ScalarSteps}
import org.thp.scalligraph.services._
import org.thp.thehive.models._

@Singleton
class TaskSrv @Inject()(caseSrv: CaseSrv, shareSrv: ShareSrv, auditSrv: AuditSrv, logSrv: LogSrv)(implicit db: Database)
    extends VertexSrv[Task, TaskSteps] {
  val caseTemplateTaskSrv = new EdgeSrv[CaseTemplateTask, CaseTemplate, Task]
  val taskUserSrv         = new EdgeSrv[TaskUser, Task, User]
  val taskLogSrv          = new EdgeSrv[TaskLog, Task, Log]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): TaskSteps = new TaskSteps(raw)

  def create(task: Task, `case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Task with Entity] = {
    val createdTask = create(task)
    for {
      share <- caseSrv
        .initSteps
        .getOrganisationShare(`case`._id)
        .getOrFail()
      _ = shareSrv.shareTaskSrv.create(ShareTask(), share, createdTask)
      _ <- auditSrv.createTask(createdTask, `case`)
    } yield createdTask
  }

  def create(task: Task, caseTemplate: CaseTemplate with Entity)(implicit graph: Graph, authContext: AuthContext): Task with Entity = {
    val createdTask = create(task)
    caseTemplateTaskSrv.create(CaseTemplateTask(), caseTemplate, createdTask)
    createdTask
  }

  def isAvailableFor(taskId: String)(implicit graph: Graph, authContext: AuthContext): Boolean =
    get(taskId).visible(authContext).exists()

  def assign(task: Task with Entity, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Unit = {
    get(task).unassign()
    taskUserSrv.create(TaskUser(), task, user)
    ()
  }

  def unassign(task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Unit =
    get(task).unassign()

  def cascadeRemove(task: Task with Entity)(implicit graph: Graph): Try[Unit] =
    for {
      _ <- Try(get(task).logs.toList.foreach(logSrv.cascadeRemove))
      r <- Try(get(task).remove())
    } yield r
}

@EntitySteps[Task]
class TaskSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Task, TaskSteps](raw) {

  def visible(implicit authContext: AuthContext): TaskSteps = newInstance(
    raw.filter(
      _.inTo[ShareTask]
        .inTo[OrganisationShare]
        .has(Key("name") of authContext.organisation)
    )
  )

  def active: TaskSteps = newInstance(raw.filterNot(_.has(Key("status") of "Cancel")))

  override def newInstance(raw: GremlinScala[Vertex]): TaskSteps = new TaskSteps(raw)

  @deprecated("", "")
  def availableFor(organisation: String): TaskSteps = ???
//    newInstance(raw.as("x").where(x ⇒ new CaseSteps(x.inTo[CaseTask]).availableFor(organisation).raw))

  def can(permission: Permission)(implicit authContext: AuthContext): TaskSteps =
    newInstance(
      raw.filter(
        _.inTo[ShareTask]
          .filter(_.outTo[ShareProfile].has(Key("permissions") of permission))
          .inTo[OrganisationShare]
          .inTo[RoleOrganisation]
          .filter(_.outTo[RoleProfile].has(Key("permissions") of permission))
          .inTo[UserRole]
          .has(Key("login") of authContext.userId)
      )
    )

  def `case` = new CaseSteps(raw.inTo[ShareTask].outTo[ShareCase])

  def logs = new LogSteps(raw.outTo[TaskLog])

  def user = new UserSteps(raw.outTo[TaskUser])

  def richTask: ScalarSteps[RichTask] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[TaskUser].values[String]("login").fold))
        )
        .map {
          case (task, user) =>
            RichTask(
              task.as[Task],
              atMostOneOf[String](user)
            )
        }
    )

  @deprecated("must not be used because it doesn't generate audit log")
  def unassign(): Unit = {
    raw.outToE[TaskUser].drop().iterate()
    ()
  }

  def remove(): Unit = {
    raw.drop().iterate()
    ()
  }
}
