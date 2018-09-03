package services

import javax.inject.{ Inject, Provider, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.json.JsObject

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import models._

import org.elastic4play.controllers.Fields
import org.elastic4play.database.{ DBRemove, ModifyConfig }
import org.elastic4play.services._

@Singleton
class LogSrv @Inject() (
    logModel: LogModel,
    taskModel: TaskModel,
    auditSrv: AuditSrv,
    taskSrvProvider: Provider[TaskSrv],
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    dbRemove: DBRemove,
    attachmentSrv: AttachmentSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  lazy val taskSrv: TaskSrv = taskSrvProvider.get

  def create(taskId: String, fields: Fields)(implicit authContext: AuthContext): Future[Log] =
    getSrv[TaskModel, Task](taskModel, taskId)
      .flatMap { task ⇒ create(task, fields) }

  def create(task: Task, fields: Fields)(implicit authContext: AuthContext): Future[Log] = {
    if (task.status() == TaskStatus.Waiting) taskSrv.update(task, Fields.empty.set("status", TaskStatus.InProgress.toString))
    createSrv[LogModel, Log, Task](logModel, task, fields.addIfAbsent("owner", authContext.userId))
  }

  def get(id: String): Future[Log] =
    getSrv[LogModel, Log](logModel, id)

  def update(id: String, fields: Fields)(implicit authContext: AuthContext): Future[Log] =
    update(id, fields, ModifyConfig.default)

  def update(id: String, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Log] =
    updateSrv[LogModel, Log](logModel, id, fields, modifyConfig)

  def delete(id: String)(implicit authContext: AuthContext): Future[Log] =
    deleteSrv[LogModel, Log](logModel, id)

  def realDelete(log: Log): Future[Unit] = {
    for {
      _ ← auditSrv.findFor(log, Some("all"), Nil)._1
        .mapAsync(1)(auditSrv.realDelete)
        .runWith(Sink.ignore)
      _ ← log.attachment().fold[Future[Unit]](Future.successful(())) { attachment ⇒
        attachmentSrv.attachmentUseCount(attachment.id).flatMap {
          case 1 ⇒ attachmentSrv.delete(attachment.id)
          case _ ⇒ Future.successful(())
        }
      }
      _ ← dbRemove(log)
    } yield ()
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Log, NotUsed], Future[Long]) = {
    findSrv[LogModel, Log](logModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, agg: Seq[Agg]): Future[JsObject] = findSrv(logModel, queryDef, agg: _*)
}