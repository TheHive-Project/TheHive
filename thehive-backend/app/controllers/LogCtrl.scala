package controllers

import scala.concurrent.ExecutionContext

import play.api.http.Status
import play.api.mvc._

import javax.inject.{Inject, Singleton}
import models.Roles
import services.LogSrv

import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.{aggReads, queryReads}
import org.elastic4play.services.{Agg, QueryDSL, QueryDef}
import org.elastic4play.{BadRequestError, Timed}

@Singleton
class LogCtrl @Inject()(
    logSrv: LogSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    components: ControllerComponents,
    implicit val ec: ExecutionContext
) extends AbstractController(components)
    with Status {

  @Timed
  def create(taskId: String): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request =>
    logSrv
      .create(taskId, request.body)
      .map(log => renderer.toOutput(CREATED, log))
  }

  @Timed
  def get(id: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request =>
    logSrv
      .get(id)
      .map(log => renderer.toOutput(OK, log))
  }

  @Timed
  def update(id: String): Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) { implicit request =>
    logSrv
      .update(id, request.body)
      .map(log => renderer.toOutput(OK, log))
  }

  @Timed
  def delete(id: String): Action[AnyContent] = authenticated(Roles.write).async { implicit request =>
    logSrv
      .delete(id)
      .map(_ => Ok(""))
  }

  @Timed
  def findInTask(taskId: String): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request =>
    import org.elastic4play.services.QueryDSL._
    val childQuery = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val query      = and(childQuery, parent("case_task", withId(taskId)))
    val range      = request.body.getString("range")
    val sort       = request.body.getStrings("sort").getOrElse(Nil)

    val (logs, total) = logSrv.find(query, range, sort)
    renderer.toOutput(OK, logs, total)
  }

  @Timed
  def find: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request =>
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort  = request.body.getStrings("sort").getOrElse(Nil)

    val (logs, total) = logSrv.find(query, range, sort)
    renderer.toOutput(OK, logs, total)
  }

  @Timed
  def stats(): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request =>
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val aggs  = request.body.getValue("stats").getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Seq[Agg]]
    logSrv.stats(query, aggs).map(s => Ok(s))
  }
}
