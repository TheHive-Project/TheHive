package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services._
import org.thp.thehive.models._

@Singleton
class TaskSrv @Inject()(caseSrv: CaseSrv, shareSrv: ShareSrv)(implicit db: Database) extends VertexSrv[Task, TaskSteps] {
  val caseTemplateTaskSrv = new EdgeSrv[CaseTemplateTask, CaseTemplate, Task]
  val taskUserSrv         = new EdgeSrv[TaskUser, Task, User]
  val taskLogSrv          = new EdgeSrv[TaskLog, Task, Log]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): TaskSteps = new TaskSteps(raw)

  def create(task: Task, `case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Task with Entity = {
    val createdTask = create(task)

    caseSrv
      .initSteps
      .getOrganisationShare(`case`._id)
      .getOrFail()
      .map(s ⇒ shareSrv.shareTaskSrv.create(ShareTask(), s, createdTask))

    createdTask
  }

  def create(task: Task, caseTemplate: CaseTemplate with Entity)(implicit graph: Graph, authContext: AuthContext): Task with Entity = {
    val createdTask = create(task)
    caseTemplateTaskSrv.create(CaseTemplateTask(), caseTemplate, createdTask)
    createdTask
  }

  def isAvailableFor(taskId: String)(implicit graph: Graph, authContext: AuthContext): Boolean =
    get(taskId).visible(authContext).isDefined

  def assign(task: Task with Entity, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Unit = {
    get(task).unassign()
    taskUserSrv.create(TaskUser(), task, user)
    ()
  }
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

  def logs = new LogSteps(raw.outTo[TaskLog])

  def user = new UserSteps(raw.outTo[TaskUser])

  def unassign(): Unit = {
    raw.outToE[TaskUser].drop().iterate()
    ()
  }
}
