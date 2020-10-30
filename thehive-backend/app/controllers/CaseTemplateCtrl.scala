package controllers

import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

import play.api.http.Status
import play.api.mvc._

import models.Roles
import services.CaseTemplateSrv

import org.elastic4play.Timed
import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.services.{AuxSrv, QueryDSL, QueryDef}

@Singleton
class CaseTemplateCtrl @Inject()(
    caseTemplateSrv: CaseTemplateSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext
) extends AbstractController(components)
    with Status {

  @Timed
  def create: Action[Fields] = authenticated(Roles.admin).async(fieldsBodyParser) { implicit request =>
    caseTemplateSrv
      .create(request.body)
      .map(caze => renderer.toOutput(CREATED, caze))
  }

  @Timed
  def get(id: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request =>
    caseTemplateSrv
      .get(id)
      .map(caze => renderer.toOutput(OK, caze))
  }

  @Timed
  def update(id: String): Action[Fields] = authenticated(Roles.admin).async(fieldsBodyParser) { implicit request =>
    caseTemplateSrv
      .update(id, request.body)
      .map(caze => renderer.toOutput(OK, caze))
  }

  @Timed
  def delete(id: String): Action[AnyContent] = authenticated(Roles.admin).async { implicit request =>
    caseTemplateSrv
      .delete(id)
      .map(_ => NoContent)
  }

  @Timed
  def find: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request =>
    val query     = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range     = request.body.getString("range")
    val sort      = request.body.getStrings("sort").getOrElse(Nil)
    val nparent   = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (caseTemplates, total) = caseTemplateSrv.find(query, range, sort)
    val caseTemplatesWithStats = auxSrv(caseTemplates, nparent, withStats, removeUnaudited = false)
    renderer.toOutput(OK, caseTemplatesWithStats, total)
  }
}
