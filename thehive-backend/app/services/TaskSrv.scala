package services

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import play.api.libs.json.{JsFalse, JsObject}

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import models._

import org.elastic4play.controllers.Fields
import org.elastic4play.database.{DBRemove, ModifyConfig}
import org.elastic4play.services._

@Singleton
class TaskSrv @Inject()(
    taskModel: TaskModel,
    caseModel: CaseModel,
    auditSrv: AuditSrv,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    dbRemove: DBRemove,
    findSrv: FindSrv,
    logSrv: LogSrv,
    implicit val mat: Materializer
) {

  def create(caseId: String, fields: Fields)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Task] =
    getSrv[CaseModel, Case](caseModel, caseId)
      .flatMap { caze ⇒
        create(caze, fields)
      }

  def create(caze: Case, fields: Fields)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Task] =
    createSrv[TaskModel, Task, Case](taskModel, caze, fields)

  def create(caseId: String, fields: Seq[Fields])(implicit authContext: AuthContext, ec: ExecutionContext): Future[Seq[Try[Task]]] =
    getSrv[CaseModel, Case](caseModel, caseId)
      .flatMap { caze ⇒
        create(caze, fields)
      }

  def create(caze: Case, fields: Seq[Fields])(implicit authContext: AuthContext, ec: ExecutionContext): Future[Seq[Try[Task]]] =
    createSrv[TaskModel, Task, Case](taskModel, fields.map(caze → _))

  def get(id: String)(implicit ec: ExecutionContext): Future[Task] =
    getSrv[TaskModel, Task](taskModel, id)

  def update(id: String, fields: Fields)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Task] =
    update(id, fields, ModifyConfig.default)

  def update(id: String, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Task] =
    getSrv[TaskModel, Task](taskModel, id)
      .flatMap { task ⇒
        update(task, fields, modifyConfig)
      }

  def update(task: Task, fields: Fields)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Task] =
    update(task, fields, ModifyConfig.default)

  def update(task: Task, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Task] = {
    // if update status from waiting to something else and owner is not set, then set owner to user
    val f =
      if (task.status() == TaskStatus.Waiting &&
          !fields.getString("status").contains(TaskStatus.Waiting.toString) &&
          !fields.contains("owner") &&
          task.owner().isEmpty)
        fields.set("owner", authContext.userId)
      else fields
    updateSrv(task, f, modifyConfig)
  }

  def closeTasksOfCase(caseIds: String*)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Seq[Try[Task]]] = {
    import org.elastic4play.services.QueryDSL._
    val filter       = and(parent("case", withId(caseIds: _*)), "status" in (TaskStatus.Waiting.toString, TaskStatus.InProgress.toString))
    val range        = Some("all")
    val completeTask = Fields.empty.set("status", TaskStatus.Completed.toString).set("flag", JsFalse)
    val cancelTask   = Fields.empty.set("status", TaskStatus.Cancel.toString).set("flag", JsFalse)

    find(filter, range, Nil)
      ._1
      .map {
        case task if task.status() == TaskStatus.Waiting ⇒ (task, cancelTask)
        case task                                        ⇒ (task, completeTask)
      }
      .runWith(Sink.seq)
      .flatMap { taskUpdate ⇒
        updateSrv(taskUpdate, ModifyConfig.default)
      }
  }

  def delete(id: String)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Task] =
    deleteSrv[TaskModel, Task](taskModel, id)

  def realDelete(task: Task)(implicit ec: ExecutionContext): Future[Unit] = {
    import org.elastic4play.services.QueryDSL._
    for {
      _ ← auditSrv
        .findFor(task, Some("all"), Nil)
        ._1
        .mapAsync(1)(auditSrv.realDelete)
        .runWith(Sink.ignore)
      _ ← logSrv
        .find(withParent(task), Some("all"), Nil)
        ._1
        .mapAsync(1)(logSrv.realDelete)
        .runWith(Sink.ignore)
      _ ← dbRemove(task)
    } yield ()
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String])(implicit ec: ExecutionContext): (Source[Task, NotUsed], Future[Long]) =
    findSrv[TaskModel, Task](taskModel, queryDef, range, sortBy)

  def stats(queryDef: QueryDef, aggs: Seq[Agg])(implicit ec: ExecutionContext): Future[JsObject] = findSrv(taskModel, queryDef, aggs: _*)
}
