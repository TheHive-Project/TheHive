package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext

import play.api.http.Status
import play.api.mvc.Controller

import org.elastic4play.Timed
import org.elastic4play.controllers.{ Authenticated, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.{ QueryDSL, QueryDef, Role }
import org.elastic4play.services.AuxSrv
import org.elastic4play.services.JsonFormat.queryReads

import services.ReportTemplateSrv

@Singleton
class ReportTemplateCtrl @Inject() (
    reportTemplateSrv: ReportTemplateSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends Controller with Status {

  @Timed
  def create = authenticated(Role.admin).async(fieldsBodyParser) { implicit request ⇒
    reportTemplateSrv.create(request.body)
      .map(reportTemplate ⇒ renderer.toOutput(CREATED, reportTemplate))
  }

  @Timed
  def get(id: String) = authenticated(Role.read).async { implicit request ⇒
    reportTemplateSrv.get(id)
      .map(reportTemplate ⇒ renderer.toOutput(OK, reportTemplate))
  }

  @Timed
  def update(id: String) = authenticated(Role.admin).async(fieldsBodyParser) { implicit request ⇒
    reportTemplateSrv.update(id, request.body)
      .map(reportTemplate ⇒ renderer.toOutput(OK, reportTemplate))
  }

  @Timed
  def delete(id: String) = authenticated(Role.admin).async { implicit request ⇒
    reportTemplateSrv.delete(id)
      .map(_ ⇒ NoContent)
  }

  @Timed
  def find = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val nparent = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (reportTemplates, total) = reportTemplateSrv.find(query, range, sort)
    val reportTemplatesWithStats = auxSrv(reportTemplates, nparent, withStats)
    renderer.toOutput(OK, reportTemplatesWithStats, total)
  }
}