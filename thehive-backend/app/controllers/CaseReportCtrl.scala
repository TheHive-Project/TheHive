package controllers

import it.innove.play.pdf.PdfGenerator
import javax.inject.{ Inject, Singleton }
import models.Roles
import org.elastic4play.Timed
import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.services.{ AuxSrv, QueryDSL, QueryDef }
import play.api.http.Status
import play.api.mvc._

import scala.concurrent.ExecutionContext

@Singleton
class CaseReportCtrl @Inject() (
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Status {

  @Timed // TODO: Add authenticated(Roles.read).async somehow
  def report(caseId: String): Action[AnyContent] = Action { implicit request ⇒
    Ok(new PdfGenerator().toBytes("Your PDF is generated", "http://localhost:9000")).as("application/pdf")
  }

  def create: Action[AnyContent] = Action { implicit request ⇒
    Ok
  }
}