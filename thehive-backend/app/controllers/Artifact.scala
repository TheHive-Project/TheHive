package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.http.Status
import play.api.libs.json.JsArray
import play.api.mvc.Controller

import org.elastic4play.{ BadRequestError, Timed }
import org.elastic4play.controllers.{ Authenticated, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.{ Agg, AuxSrv }
import org.elastic4play.services.{ QueryDSL, QueryDef, Role }
import org.elastic4play.services.JsonFormat.{ aggReads, queryReads }

import services.ArtifactSrv

@Singleton
class ArtifactCtrl @Inject() (
    artifactSrv: ArtifactSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends Controller with Status {

  @Timed
  def create(caseId: String) = authenticated(Role.write).async(fieldsBodyParser) { implicit request ⇒
    val fields = request.body
    val data = fields.getStrings("data")
      .getOrElse(fields.getString("data").toSeq)
      .map(_.trim) // most observables don't accept leading or trailing space
      .filterNot(_.isEmpty)
    // if data is not multivalued, use simple API (not bulk API)
    if (data.isEmpty) {
      artifactSrv.create(caseId, fields)
        .map(artifact ⇒ renderer.toOutput(CREATED, artifact))
    }
    else if (data.length == 1) {
      artifactSrv.create(caseId, fields.set("data", data.head))
        .map(artifact ⇒ renderer.toOutput(CREATED, artifact))
    }
    else {
      val multiFields = data.map(fields.set("data", _))
      artifactSrv.create(caseId, multiFields)
        .map(multiResult ⇒ renderer.toMultiOutput(CREATED, multiResult))
    }
  }

  @Timed
  def get(id: String) = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    artifactSrv.get(id, request.body.getStrings("fields").map("dataType" +: _))
      .map(artifact ⇒ renderer.toOutput(OK, artifact))
  }

  @Timed
  def update(id: String) = authenticated(Role.write).async(fieldsBodyParser) { implicit request ⇒
    artifactSrv.update(id, request.body)
      .map(artifact ⇒ renderer.toOutput(OK, artifact))
  }

  @Timed
  def bulkUpdate() = authenticated(Role.write).async(fieldsBodyParser) { implicit request ⇒
    request.body.getStrings("ids").fold(Future.successful(Ok(JsArray()))) { ids ⇒
      artifactSrv.bulkUpdate(ids, request.body.unset("ids")).map(multiResult ⇒ renderer.toMultiOutput(OK, multiResult))
    }
  }

  @Timed
  def delete(id: String) = authenticated(Role.write).async { implicit request ⇒
    artifactSrv.delete(id)
      .map(_ ⇒ NoContent)
  }

  @Timed
  def findInCase(caseId: String) = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    import org.elastic4play.services.QueryDSL._
    val childQuery = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val query = and(childQuery, "_parent" ~= caseId)
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)

    val (artifacts, total) = artifactSrv.find(query, range, sort)
    renderer.toOutput(OK, artifacts, total)
  }

  @Timed
  def find() = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val nparent = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (artifacts, total) = artifactSrv.find(query, range, sort)
    val artifactWithCase = auxSrv(artifacts, nparent, withStats)
    renderer.toOutput(OK, artifactWithCase, total)
  }

  @Timed
  def findSimilar(artifactId: String) = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    artifactSrv.get(artifactId).flatMap { artifact ⇒
      val range = request.body.getString("range")
      val sort = request.body.getStrings("sort").getOrElse(Nil)

      val (artifacts, total) = artifactSrv.findSimilar(artifact, range, sort)
      val artifactWithCase = auxSrv(artifacts, 1, false)
      renderer.toOutput(OK, artifactWithCase, total)
    }
  }

  @Timed
  def stats() = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val aggs = request.body.getValue("stats").getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Seq[Agg]]
    artifactSrv.stats(query, aggs).map(s ⇒ Ok(s))
  }
}