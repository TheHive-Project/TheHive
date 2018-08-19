package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.io.Source

import play.api.Logger
import play.api.http.Status
import play.api.mvc._
import play.api.libs.json.{ JsTrue, JsFalse }

import models.Roles
import services.CaseReportingTemplateSrv

import org.elastic4play.{ BadRequestError, Timed }
import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer, FileInputValue }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.services.{ AuxSrv, QueryDSL, QueryDef }

@Singleton
class CaseReportingTemplateCtrl @Inject() (
    caseReportingTemplateSrv: CaseReportingTemplateSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Status {

  private[CaseReportingTemplateCtrl] lazy val logger = Logger(getClass)

  @Timed
  def create: Action[Fields] = authenticated(Roles.admin).async(fieldsBodyParser) { implicit request ⇒
    caseReportingTemplateSrv.create(request.body)
      .map(caseReporting ⇒ renderer.toOutput(CREATED, caseReporting))
  }

  @Timed
  def get(id: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request ⇒
    caseReportingTemplateSrv.get(id)
      .map(caseReporting ⇒ renderer.toOutput(OK, caseReporting))
  }

  @Timed
  def update(id: String): Action[Fields] = authenticated(Roles.admin).async(fieldsBodyParser) { implicit request ⇒
    val updates = Fields.empty
      .set("title", request.body.getString("title").getOrElse(""))
      .set("content", request.body.getString("content").getOrElse(""))
      .set("isDefault", request.body.getValue("isDefault").getOrElse(JsFalse))

    caseReportingTemplateSrv.update(id, updates)
      .map(caseReporting ⇒ renderer.toOutput(OK, caseReporting))
  }

  @Timed
  def delete(id: String): Action[AnyContent] = authenticated(Roles.admin).async { implicit request ⇒
    caseReportingTemplateSrv.delete(id)
      .map(_ ⇒ NoContent)
  }

  @Timed
  def find: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val nparent = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (caseReportingTemplates, total) = caseReportingTemplateSrv.find(query, range, sort)
    val caseReportingTemplatesWithStats = auxSrv(caseReportingTemplates, nparent, withStats, removeUnaudited = false)
    renderer.toOutput(OK, caseReportingTemplatesWithStats, total)
  }

  @Timed
  def importTemplate: Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) { implicit request ⇒
    val file = request.body.get("template") match {
      case Some(FileInputValue(_, filepath, _)) ⇒ Source.fromFile(filepath.toFile)
      case _                                    ⇒ throw BadRequestError("")
    }
    val title = request.body.get("template")

    val templateTitle = title.toString().split('(')(2).split(',')(0)
    val templateContent = file.mkString

    val caseReportingTemplateFields = Fields.empty
      .set("title", templateTitle)
      .set("content", templateContent)
      .set("isDefault", JsFalse)
    caseReportingTemplateSrv.create(caseReportingTemplateFields)
      .recoverWith {
        case NonFatal(_) ⇒
          caseReportingTemplateSrv.update(templateTitle, Fields.empty.set("content", templateContent))
      }
      .map(_.id → JsTrue)
      .recoverWith {
        case NonFatal(e) ⇒
          logger.error(s"The import of the case reporting template $templateTitle has failed", e)
          Future.successful(templateTitle → JsFalse)
      }
    Future { renderer.toOutput(OK, templateTitle) }
  }
}

