package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext

import play.api.mvc.Controller
import play.api.http.Status

import org.elastic4play.controllers.{ Fields, Authenticated, Renderer, FieldsBodyParser }
import org.elastic4play.services.{ Role, QueryDSL, AuxSrv, QueryDef }
import org.elastic4play.services.JsonFormat._
import org.elastic4play.models.JsonFormat._
import services.AnalyzerSrv
import models.JsonFormat._
import org.elastic4play.Timed

@Singleton
class AnalyzerCtrl @Inject() (
    analyzerSrv: AnalyzerSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends Controller with Status {

  @Timed
  def get(id: String) = authenticated(Role.read).async { implicit request ⇒
    analyzerSrv.get(id.replaceAll("\\.", "_")) // FIXME replace "." by "_" should not be usefull after migration
      .map(analyzer ⇒ renderer.toOutput(OK, analyzer))
  }

  @Timed
  def find = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val nparent = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (analyzers, total) = analyzerSrv.find(query, range, sort)
    val analyzersWithStats = auxSrv(analyzers, nparent, withStats)
    renderer.toOutput(OK, analyzersWithStats, total)
  }

  @Timed
  def getReport(analyzerId: String, flavor: String) = authenticated(Role.read).async { request ⇒
    analyzerSrv.getReport(analyzerId.replaceAll("\\.", "_"), flavor) // FIXME replace "." by "_" should not be usefull after migration
      .map { reportTemplate ⇒ Ok(reportTemplate) }
  }
}