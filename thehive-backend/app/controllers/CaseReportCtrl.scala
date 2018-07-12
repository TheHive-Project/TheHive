package controllers

import javax.inject.{ Inject, Singleton }
import org.elastic4play.Timed
import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.services.{ AuxSrv, QueryDSL, QueryDef }
import play.api.http.Status
import play.api.mvc._
import models.Roles
import services.CaseReportSrv

import scala.concurrent.ExecutionContext
import it.innove.play.pdf.PdfGenerator

import play.api.libs.json.JsObject

@Singleton
class CaseReportCtrl @Inject() (
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    caseReportSrv: CaseReportSrv,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Status {

  @Timed // TODO: Add authenticated(Roles.read).async somehow
  def report(caseId: String): Action[AnyContent] = Action { implicit request ⇒

    Ok(new PdfGenerator().toBytes("Your PDF is generated", "http://localhost:9000")).as("application/pdf")
  }

  def create: Action[AnyContent] = Action { implicit request ⇒
    Ok
  }

  def update: Action[AnyContent] = Action { implicit request ⇒
    Ok
  }

  @Timed
  def find: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = QueryDSL.any
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)

    val (reports, total) = caseReportSrv.find(query, range, sort)
    renderer.toOutput(OK, reports.map(_.toJson), total)
  }
}