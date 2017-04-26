package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe
import play.api.http.Status
import play.api.mvc.{ Action, AnyContent, Controller }
import org.elastic4play.Timed
import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.{ QueryDSL, QueryDef, Role }
import org.elastic4play.services.AuxSrv
import org.elastic4play.services.JsonFormat.queryReads
import services.CaseTemplateSrv

@Singleton
class CaseTemplateCtrl @Inject() (
    caseTemplateSrv: CaseTemplateSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends Controller with Status {

  @Timed
  def create: Action[Fields] = authenticated(Role.admin).async(fieldsBodyParser) { implicit request ⇒
    caseTemplateSrv.create(request.body)
      .map(caze ⇒ renderer.toOutput(CREATED, caze))
  }

  @Timed
  def get(id: String): Action[AnyContent] = authenticated(Role.read).async { implicit request ⇒
    caseTemplateSrv.get(id)
      .map(caze ⇒ renderer.toOutput(OK, caze))
  }

  @Timed
  def update(id: String): Action[Fields] = authenticated(Role.admin).async(fieldsBodyParser) { implicit request ⇒
    caseTemplateSrv.update(id, request.body)
      .map(caze ⇒ renderer.toOutput(OK, caze))
  }

  @Timed
  def delete(id: String): Action[AnyContent] = authenticated(Role.admin).async { implicit request ⇒
    caseTemplateSrv.delete(id)
      .map(_ ⇒ NoContent)
  }

  @Timed
  def find: Action[Fields] = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val nparent = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (caseTemplates, total) = caseTemplateSrv.find(query, range, sort)
    val caseTemplatesWithStats = auxSrv(caseTemplates, nparent, withStats, removeUnaudited = false)
    renderer.toOutput(OK, caseTemplatesWithStats, total)
  }
}