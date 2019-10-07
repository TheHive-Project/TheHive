package org.thp.thehive.services

import java.util.Date

import scala.util.{Failure, Success, Try}

import play.api.libs.json.{JsNull, JsObject, Json}

import gremlin.scala._
import javax.inject.{Inject, Provider, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.thehive.models.{TaskStatus, _}

@Singleton
class TaskSrv @Inject()(caseSrvProvider: Provider[CaseSrv], auditSrv: AuditSrv, logSrv: LogSrv)(implicit db: Database)
    extends VertexSrv[Task, TaskSteps] {

  lazy val caseSrv: CaseSrv = caseSrvProvider.get
  val caseTemplateTaskSrv   = new EdgeSrv[CaseTemplateTask, CaseTemplate, Task]
  val taskUserSrv           = new EdgeSrv[TaskUser, Task, User]
  val taskLogSrv            = new EdgeSrv[TaskLog, Task, Log]

  def create(e: Task)(implicit graph: Graph, authContext: AuthContext): Try[Task with Entity] = createEntity(e)

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): TaskSteps = new TaskSteps(raw)

  def isAvailableFor(taskId: String)(implicit graph: Graph, authContext: AuthContext): Boolean =
    getByIds(taskId).visible(authContext).exists()

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
          c <- taskSteps.newInstance().`case`.getOrFail()
          t <- taskSteps.getOrFail()
          _ <- auditSrv.task.update(t, c, updatedFields)
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
  def updateStatus(t: Task with Entity, o: User with Entity, s: TaskStatus.Value)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Task with Entity] = {
    def setStatus() = get(t).update("status" -> s)

    s match {
      case TaskStatus.Cancel | TaskStatus.Waiting => setStatus()
      case TaskStatus.Completed =>
        t.endDate.fold(get(t).update("status" -> s, "endDate" -> Some(new Date())))(_ => setStatus())

      case TaskStatus.InProgress =>
        for {
          _       <- get(t).user.headOption().fold(assign(t, o))(_ => Success(()))
          updated <- t.startDate.fold(get(t).update("status" -> s, "startDate" -> Some(new Date())))(_ => setStatus())
        } yield updated

      case _ => Failure(new Exception(s"Invalid TaskStatus $s for update"))
    }
  }

  def assign(task: Task with Entity, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      case0 <- get(task).`case`.getOrFail()
      _ = get(task).unassign()
      _ = taskUserSrv.create(TaskUser(), task, user)
      _ <- auditSrv.task.update(task, case0, Json.obj("assignee" -> user.login))
    } yield ()
}

@EntitySteps[Task]
class TaskSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Task](raw) {

  def visible(implicit authContext: AuthContext): TaskSteps = newInstance(
    raw.filter(
      _.inTo[ShareTask]
        .inTo[OrganisationShare]
        .has(Key("name") of authContext.organisation)
    )
  )

  override def newInstance(newRaw: GremlinScala[Vertex]): TaskSteps = new TaskSteps(newRaw)
  override def newInstance(): TaskSteps                             = new TaskSteps(raw.clone())

  def active: TaskSteps = newInstance(raw.filterNot(_.has(Key("status") of "Cancel")))

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

  def richTask: Traversal[RichTask, RichTask] =
    Traversal(
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
