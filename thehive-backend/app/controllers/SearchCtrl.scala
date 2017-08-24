package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext

import play.api.http.Status
import play.api.mvc.{ AbstractController, Action, ControllerComponents }

import org.elastic4play.Timed
import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.services._

@Singleton
class SearchCtrl @Inject() (
    findSrv: FindSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Status {

  @Timed
  def find(): Action[Fields] = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    import org.elastic4play.services.QueryDSL._
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val nparent = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (entities, total) = findSrv(None, and(query, "status" ~!= "Deleted", not(or(ofType("audit"), ofType("data"), ofType("user"), ofType("analyzer"), ofType("misp")))), range, sort)
    val entitiesWithStats = auxSrv(entities, nparent, withStats, removeUnaudited = true)
    renderer.toOutput(OK, entitiesWithStats, total)
  }
}