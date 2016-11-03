package connectors.misp

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{ JsArray, Json }
import play.api.mvc.{ Action, Controller }
import play.api.routing.SimpleRouter
import play.api.routing.sird.{ GET, POST, UrlContext }

import org.elastic4play.{ NotFoundError, Timed }
import org.elastic4play.BadRequestError
import org.elastic4play.JsonFormat.tryWrites
import org.elastic4play.controllers.{ Authenticated, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.{ QueryDSL, QueryDef, Role }
import org.elastic4play.services.Agg
import org.elastic4play.services.JsonFormat.{ aggReads, queryReads }

import connectors.Connector
import services.CaseSrv
import org.elastic4play.utils.Collection
import play.api.libs.json.JsString

@Singleton
class MispCtrl @Inject() (
    mispSrv: MispSrv,
    caseSrv: CaseSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends Controller with Connector with Status {
  val name = "misp"
  val log = Logger(getClass)
  val router = SimpleRouter {
    case GET(p"/_update")                 => update
    case POST(p"/_search")                => find
    case POST(p"/_stats")                 => stats
    case GET(p"/get/$mispId<[^/]*>")      => getEvent(mispId)
    case GET(p"/ignore/$mispId<[^/]*>")   => ignore(mispId)
    case GET(p"/follow/$mispId<[^/]*>")   => follow(mispId)
    case GET(p"/unfollow/$mispId<[^/]*>") => unfollow(mispId)
    case POST(p"/case/$mispId<[^/]*>")    => createCase(mispId)
    case r                                => throw NotFoundError(s"${r.uri} not found")
  }

  @Timed
  def update = authenticated(Role.read).async { implicit request =>
    mispSrv.update()
      .map { m => Ok(Json.toJson(m)) }
  }

  @Timed
  def find = authenticated(Role.read).async(fieldsBodyParser) { implicit request =>
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Seq("-eventId"))

    val (events, total) = mispSrv.find(query, range, sort)
    renderer.toOutput(OK, events, total)
  }

  @Timed
  def stats = authenticated(Role.read).async(fieldsBodyParser) { implicit request =>
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val aggs = request.body.getValue("stats").getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Seq[Agg]]
    mispSrv.stats(query, aggs).map(s => Ok(s))
  }

  @Timed
  def ignore(mispId: String) = authenticated(Role.write).async { implicit request =>
    mispSrv.ignoreEvent(mispId).map(_ => NoContent)
  }

  @Timed
  def follow(mispId: String) = authenticated(Role.write).async { implicit request =>
    mispSrv.setFollowEvent(mispId, true).map(_ => NoContent)
  }

  @Timed
  def unfollow(mispId: String) = authenticated(Role.write).async { implicit request =>
    mispSrv.setFollowEvent(mispId, false).map(_ => NoContent)
  }

  @Timed
  def createCase(mispId: String) = authenticated(Role.write).async { implicit request =>
    for {
      (caze, artifacts) <- mispSrv.createCase(mispId)
      (importedArtifacts, importArtifactErrors) = Collection.partitionTry(artifacts)
      _ = log.info(s"${importedArtifacts.size} aritfact(s) imported")
      _ = if (!importArtifactErrors.isEmpty) log.warn(s"artifact import errors : ${importArtifactErrors.map(t => t.getMessage + ":" + t.getStackTrace().mkString("", "\n\t", "\n"))}")
    } yield renderer.toOutput(OK, caze)
  }

  @Timed
  def getEvent(mispId: String) = authenticated(Role.write).async { implicit request =>
    for {
      misp <- mispSrv.getMisp(mispId)
      attributes <- mispSrv.getAttributes(misp)
      fileAttributes = attributes.collect {
        case a if a.tpe == "malware-sample" || a.tpe == "attachment" => Json.obj(
            "dataType" -> "file",
                  "message" -> a.comment,
                  "tags" -> Seq(s"src:${misp.org()}"),
                  "data" -> JsString(a.value))
      }
    } yield renderer.toOutput(OK, misp.toJson + ("attributes" -> JsArray(fileAttributes ++ attributes.flatMap(a => mispSrv.convertAttribute(a)))))
  }
}