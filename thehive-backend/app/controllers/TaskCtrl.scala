package controllers

import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

import play.api.http.Status
import play.api.mvc._

import models.Roles
import services.TaskSrv

import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.{aggReads, queryReads}
import org.elastic4play.services._
import org.elastic4play.{BadRequestError, Timed}

@Singleton
class TaskCtrl @Inject()(
    taskSrv: TaskSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    components: ControllerComponents,
    implicit val ec: ExecutionContext
) extends AbstractController(components)
    with Status {

  @Timed
  def create(caseId: String): Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) { implicit request ⇒
    taskSrv
      .create(caseId, request.body)
      .map(task ⇒ renderer.toOutput(CREATED, task))
  }

  @Timed
  def get(id: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request ⇒
    taskSrv
      .get(id)
      .map(task ⇒ renderer.toOutput(OK, task))
  }

  @Timed
  def update(id: String): Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) { implicit request ⇒
    taskSrv
      .update(id, request.body)
      .map(task ⇒ renderer.toOutput(OK, task))
  }

  @Timed
  def delete(id: String): Action[AnyContent] = authenticated(Roles.write).async { implicit request ⇒
    taskSrv
      .delete(id)
      .map(_ ⇒ NoContent)
  }

  @Timed
  def findInCase(caseId: String): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    import org.elastic4play.services.QueryDSL._
    val childQuery = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val query      = and(childQuery, withParent("case", caseId))
    val range      = request.body.getString("range")
    val sort       = request.body.getStrings("sort").getOrElse(Nil)

    val (tasks, total) = taskSrv.find(query, range, sort)
    renderer.toOutput(OK, tasks, total)
  }

  @Timed
  def find: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query     = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range     = request.body.getString("range")
    val sort      = request.body.getStrings("sort").getOrElse(Nil)
    val nparent   = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("stats").getOrElse(false)

    val (tasks, total) = taskSrv.find(query, range, sort)
    val tasksWithStats = auxSrv.apply(tasks, nparent, withStats, removeUnaudited = false)
    renderer.toOutput(OK, tasksWithStats, total)
  }

  @Timed
  def stats(): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val aggs  = request.body.getValue("stats").getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Seq[Agg]]
    taskSrv.stats(query, aggs).map(s ⇒ Ok(s))
  }
}
