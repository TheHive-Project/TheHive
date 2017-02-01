package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }

import play.api.libs.json.{ JsBoolean, JsObject }

import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ Agg, AuthContext, CreateSrv, DeleteSrv, FindSrv, GetSrv, QueryDef, UpdateSrv }

import models.{ Case, CaseModel, Task, TaskModel, TaskStatus }

@Singleton
class TaskSrv @Inject() (
    taskModel: TaskModel,
    caseModel: CaseModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  def create(caseId: String, fields: Fields)(implicit authContext: AuthContext): Future[Task] =
    getSrv[CaseModel, Case](caseModel, caseId)
      .flatMap { caze ⇒ create(caze, fields) }

  def create(caze: Case, fields: Fields)(implicit authContext: AuthContext): Future[Task] =
    createSrv[TaskModel, Task, Case](taskModel, caze, fields)

  def create(caseId: String, fields: Seq[Fields])(implicit authContext: AuthContext): Future[Seq[Try[Task]]] =
    getSrv[CaseModel, Case](caseModel, caseId)
      .flatMap { caze ⇒ create(caze, fields) }

  def create(caze: Case, fields: Seq[Fields])(implicit authContext: AuthContext): Future[Seq[Try[Task]]] =
    createSrv[TaskModel, Task, Case](taskModel, fields.map(caze → _))

  def get(id: String) =
    getSrv[TaskModel, Task](taskModel, id)

  def update(id: String, fields: Fields)(implicit authContext: AuthContext): Future[Task] = {
    getSrv[TaskModel, Task](taskModel, id)
      .flatMap { task ⇒ update(task, fields) }
  }

  def update(task: Task, fields: Fields)(implicit authContext: AuthContext): Future[Task] = {
    // if update status from waiting to something else and owner is not set, then set owner to user
    val f = if (task.status() == TaskStatus.Waiting &&
      fields.getString("status").filterNot(_ == TaskStatus.Waiting.toString).isDefined &&
      !fields.contains("owner") &&
      task.owner().isEmpty)
      fields.set("owner", authContext.userId)
    else fields
    updateSrv(task, f)
  }

  def closeTasksOfCase(caseIds: String*)(implicit authContext: AuthContext): Future[Seq[Try[Task]]] = {
    import org.elastic4play.services.QueryDSL._
    val filter = and(parent("case", withId(caseIds: _*)), "status" in (TaskStatus.Waiting.toString, TaskStatus.InProgress.toString))
    val range = Some("all")
    val completeTask = Fields.empty.set("status", TaskStatus.Completed.toString).set("flag", JsBoolean(false))
    val cancelTask = Fields.empty.set("status", TaskStatus.Cancel.toString).set("flag", JsBoolean(false))

    find(filter, range, Nil)
      ._1
      .map {
        case task if task.status() == TaskStatus.Waiting ⇒ (task, cancelTask)
        case task                                        ⇒ (task, completeTask)
      }
      .runWith(Sink.seq)
      .flatMap { taskUpdate ⇒ updateSrv(taskUpdate) }
  }

  def delete(id: String)(implicit authContext: AuthContext): Future[Task] =
    deleteSrv[TaskModel, Task](taskModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Task, NotUsed], Future[Long]) = {
    findSrv[TaskModel, Task](taskModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, aggs: Seq[Agg]): Future[JsObject] = findSrv(taskModel, queryDef, aggs: _*)
}