package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.{AuthorizationError, EntitySteps}
import org.thp.thehive.models._

@Singleton
class TaskSrv @Inject()(implicit db: Database) extends VertexSrv[Task, TaskSteps] {
  val taskCaseSrv         = new EdgeSrv[TaskCase, Task, Case]
  val taskCaseTemplateSrv = new EdgeSrv[TaskCaseTemplate, Task, CaseTemplate]
  val taskUserSrv         = new EdgeSrv[TaskUser, Task, User]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): TaskSteps = new TaskSteps(raw)

  def create(task: Task, `case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Task with Entity = {
    val createdTask = create(task)
    taskCaseSrv.create(TaskCase(), createdTask, `case`)
    createdTask
  }

  def create(task: Task, caseTemplate: CaseTemplate with Entity)(implicit graph: Graph, authContext: AuthContext): Task with Entity = {
    val createdTask = create(task)
    taskCaseTemplateSrv.create(TaskCaseTemplate(), createdTask, caseTemplate)
    createdTask
  }

  def isAvailableFor(taskId: String)(implicit graph: Graph, authContext: AuthContext): Boolean =
    get(taskId).availableFor(Some(authContext)).isDefined

  def assign(task: Task with Entity, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Unit = {
    get(task).unassign()
    taskUserSrv.create(TaskUser(), task, user)
    ()
  }
}

@EntitySteps[Task]
class TaskSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Task, TaskSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): TaskSteps = new TaskSteps(raw)

  def availableFor(authContext: Option[AuthContext]): TaskSteps =
    availableFor(authContext.getOrElse(throw AuthorizationError("access denied")).organisation)

  def availableFor(organisation: String): TaskSteps =
    newInstance(raw.where(x ⇒ new CaseSteps(x.outTo[TaskCase]).availableFor(organisation).raw))

  def logs = new LogSteps(raw.inTo[LogTask])

  def user = new UserSteps(raw.outTo[TaskUser])

  def unassign(): Unit = {
    raw.outToE[TaskUser].drop().iterate()
    ()
  }
}
