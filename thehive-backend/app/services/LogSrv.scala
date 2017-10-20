package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.json.JsObject

import akka.NotUsed
import akka.stream.scaladsl.Source
import models.{ Log, LogModel, Task, TaskModel }

import org.elastic4play.controllers.Fields
import org.elastic4play.services._

@Singleton
class LogSrv @Inject() (
    logModel: LogModel,
    taskModel: TaskModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext) {

  def create(taskId: String, fields: Fields)(implicit authContext: AuthContext): Future[Log] =
    getSrv[TaskModel, Task](taskModel, taskId)
      .flatMap { task ⇒ create(task, fields) }

  def create(task: Task, fields: Fields)(implicit authContext: AuthContext): Future[Log] = {
    createSrv[LogModel, Log, Task](logModel, task, fields.addIfAbsent("owner", authContext.userId))
  }

  def get(id: String): Future[Log] =
    getSrv[LogModel, Log](logModel, id)

  def update(id: String, fields: Fields)(implicit Context: AuthContext): Future[Log] =
    updateSrv[LogModel, Log](logModel, id, fields)

  def delete(id: String)(implicit Context: AuthContext): Future[Log] =
    deleteSrv[LogModel, Log](logModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Log, NotUsed], Future[Long]) = {
    findSrv[LogModel, Log](logModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, agg: Agg*): Future[JsObject] = findSrv(logModel, queryDef, agg: _*)
}