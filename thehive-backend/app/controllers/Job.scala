package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe

import play.api.http.Status
import play.api.mvc.Controller

import org.elastic4play.{ BadRequestError, Timed }
import org.elastic4play.controllers.{ Authenticated, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.{ QueryDSL, QueryDef, Role }
import org.elastic4play.services.Agg
import org.elastic4play.services.JsonFormat.{ aggReads, queryReads }

import services.JobSrv

@Singleton
class JobCtrl @Inject() (
    jobSrv: JobSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends Controller with Status {

  @Timed
  def create(artifactId: String) = authenticated(Role.write).async(fieldsBodyParser) { implicit request ⇒
    jobSrv.create(artifactId, request.body)
      .map(job ⇒ renderer.toOutput(CREATED, job))
  }

  @Timed
  def get(id: String) = authenticated(Role.read).async { implicit request ⇒
    jobSrv.get(id)
      .map(artifact ⇒ renderer.toOutput(OK, artifact))
  }

  @Timed
  def findInArtifact(artifactId: String) = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    import org.elastic4play.services.QueryDSL._
    val childQuery = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val query = and(childQuery, "_parent" ~= artifactId)
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)

    val (jobs, total) = jobSrv.find(query, range, sort)
    renderer.toOutput(OK, jobs, total)
  }

  @Timed
  def find = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)

    val (jobs, total) = jobSrv.find(query, range, sort)
    renderer.toOutput(OK, jobs, total)
  }

  @Timed
  def stats() = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val agg = request.body.getValue("stats").getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Agg]
    jobSrv.stats(query, agg).map(s ⇒ Ok(s))
  }
}