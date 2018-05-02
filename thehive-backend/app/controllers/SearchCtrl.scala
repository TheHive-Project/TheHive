package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.http.Status
import play.api.libs.json.JsObject
import play.api.mvc.{ AbstractController, Action, ControllerComponents }

import models.Roles

import org.elastic4play.{ BadRequestError, Timed }
import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.services.JsonFormat.{ aggReads, queryReads }
import org.elastic4play.services._

@Singleton
class SearchCtrl @Inject() (
    findSrv: FindSrv,
    auxSrv: AuxSrv,
    modelSrv: ModelSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Status {

  @Timed
  def find(): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
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

  @Timed
  def stats(): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    import org.elastic4play.services.QueryDSL._
    val globalQuery = request.body.getValue("query").flatMap(_.asOpt[QueryDef]).toList
    Future
      .traverse(request.body.getValue("stats")
        .getOrElse(throw BadRequestError("Parameter \"stats\" is missing"))
        .as[Seq[JsObject]]) { statsJson ⇒

        val query = (statsJson \ "query").asOpt[QueryDef].toList
        val agg = (statsJson \ "stats").getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Seq[Agg]]
        val modelName = (statsJson \ "model").getOrElse(throw BadRequestError("Parameter \"model\" is missing")).as[String]
        val model = modelSrv.apply(modelName).getOrElse(throw BadRequestError(s"Model $modelName doesn't exist"))
        findSrv.apply(model, and(globalQuery ::: query), agg: _*)
      }
      .map { statsResults ⇒
        renderer.toOutput(OK, statsResults.reduceOption(_ deepMerge _).getOrElse(JsObject.empty))
      }
  }
}