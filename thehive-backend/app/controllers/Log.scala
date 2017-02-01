package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe

import play.api.http.Status
import play.api.mvc.Controller

import org.elastic4play.Timed
import org.elastic4play.controllers.{ Authenticated, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.{ QueryDSL, QueryDef, Role }
import org.elastic4play.services.JsonFormat.queryReads

import services.LogSrv

@Singleton
class LogCtrl @Inject() (
    logSrv: LogSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends Controller with Status {

  @Timed
  def create(taskId: String) = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    logSrv.create(taskId, request.body)
      .map(log ⇒ renderer.toOutput(CREATED, log))
  }

  @Timed
  def get(id: String) = authenticated(Role.read).async { implicit request ⇒
    logSrv.get(id)
      .map(log ⇒ renderer.toOutput(OK, log))
  }

  @Timed
  def update(id: String) = authenticated(Role.write).async(fieldsBodyParser) { implicit request ⇒
    logSrv.update(id, request.body)
      .map(log ⇒ renderer.toOutput(OK, log))
  }

  @Timed
  def delete(id: String) = authenticated(Role.write).async { implicit request ⇒
    logSrv.delete(id)
      .map(_ ⇒ Ok(""))
  }

  @Timed
  def findInTask(taskId: String) = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    import org.elastic4play.services.QueryDSL._
    val childQuery = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val query = and(childQuery, "_parent" ~= taskId)
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)

    val (logs, total) = logSrv.find(query, range, sort)
    renderer.toOutput(OK, logs, total)
  }

  @Timed
  def find = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)

    val (logs, total) = logSrv.find(query, range, sort)
    renderer.toOutput(OK, logs, total)
  }
}