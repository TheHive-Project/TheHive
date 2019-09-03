package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Provider, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity, ScalarSteps}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.thehive.models._
import play.api.libs.json.{JsNull, JsObject, Json}

import scala.util.Try

@Singleton
class TaskSrv @Inject()(caseSrvProvider: Provider[CaseSrv], shareSrv: ShareSrv, auditSrv: AuditSrv, logSrv: LogSrv)(implicit db: Database)
    extends VertexSrv[Task, TaskSteps] {

  lazy val caseSrv: CaseSrv = caseSrvProvider.get
  val caseTemplateTaskSrv   = new EdgeSrv[CaseTemplateTask, CaseTemplate, Task]
  val taskUserSrv           = new EdgeSrv[TaskUser, Task, User]
  val taskLogSrv            = new EdgeSrv[TaskLog, Task, Log]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): TaskSteps = new TaskSteps(raw)

  def isAvailableFor(taskId: String)(implicit graph: Graph, authContext: AuthContext): Boolean =
    getByIds(taskId).visible(authContext).exists()

  def assign(task: Task with Entity, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      case0 <- get(task).`case`.getOrFail()
      _ = get(task).unassign()
      _ = taskUserSrv.create(TaskUser(), task, user)
      _ <- auditSrv.task.update(task, case0, Json.obj("assignee" -> user.login))
    } yield ()

  def unassign(task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      case0 <- get(task).`case`.getOrFail()
      _ = get(task).unassign()
      _ <- auditSrv.task.update(task, case0, Json.obj("assignee" -> JsNull))
    } yield ()

  def cascadeRemove(task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      case0 <- get(task).`case`.getOrFail()
      _     <- get(task).logs.toIterator.toTry(logSrv.cascadeRemove(_))
      _ = get(task).remove()
      _ <- auditSrv.task.delete(task, Some(case0))
    } yield ()

  override def update(
      steps: TaskSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(TaskSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (taskSteps, updatedFields) =>
        for {
          c <- taskSteps.clone().`case`.getOrFail()
          t <- taskSteps.getOrFail()
        } yield auditSrv.task.update(t, c, updatedFields)
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

  def active: TaskSteps = newInstance(raw.filterNot(_.has(Key("status") of "Cancel")))

  override def newInstance(raw: GremlinScala[Vertex]): TaskSteps = new TaskSteps(raw)

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

  def unassign(): Unit = {
    raw.outToE[TaskUser].drop().iterate()
    ()
  }
}
