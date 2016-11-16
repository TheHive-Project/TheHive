package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.runtime.universe
import scala.util.{ Failure, Success }

import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{ JsArray, JsObject, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.Controller

import org.elastic4play.{ BadRequestError, CreateError, Timed }
import org.elastic4play.controllers.{ Authenticated, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.{ baseModelEntityWrites, multiFormat }
import org.elastic4play.services.{ Agg, AuxSrv }
import org.elastic4play.services.{ QueryDSL, QueryDef, Role }
import org.elastic4play.services.JsonFormat.{ aggReads, queryReads }

import models.{ Case, CaseStatus }
import services.{ CaseSrv, TaskSrv }
import services.CaseMergeSrv

@Singleton
class CaseCtrl @Inject() (
    caseSrv: CaseSrv,
    caseMergeSrv: CaseMergeSrv,
    taskSrv: TaskSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) extends Controller with Status {

  val log = Logger(getClass)

  @Timed
  def create() = authenticated(Role.write).async(fieldsBodyParser) { implicit request =>
    caseSrv.create(request.body)
      .map(caze => renderer.toOutput(CREATED, caze))
  }

  @Timed
  def get(id: String) = authenticated(Role.read).async { implicit request =>
    caseSrv.get(id)
      .map(caze => renderer.toOutput(OK, caze))
  }

  @Timed
  def update(id: String) = authenticated(Role.write).async(fieldsBodyParser) { implicit request =>
    val isCaseClosing = request.body.getString("status").filter(_ == CaseStatus.Resolved.toString).isDefined

    for {
      // Closing the case, so lets close the open tasks
      caze <- caseSrv.update(id, request.body)
      closedTasks <- if (isCaseClosing) taskSrv.closeTasksOfCase(id) else Future.successful(Nil) // FIXME log warning if closedTasks contains errors
    } yield renderer.toOutput(OK, caze)
  }

  @Timed
  def bulkUpdate() = authenticated(Role.write).async(fieldsBodyParser) { implicit request =>
    val isCaseClosing = request.body.getString("status").filter(_ == CaseStatus.Resolved.toString).isDefined

    request.body.getStrings("ids").fold(Future.successful(Ok(JsArray()))) { ids =>
      if (isCaseClosing) taskSrv.closeTasksOfCase(ids: _*) // FIXME log warning if closedTasks contains errors
      caseSrv.bulkUpdate(ids, request.body.unset("ids")).map(multiResult => renderer.toMultiOutput(OK, multiResult))
    }
  }

  @Timed
  def delete(id: String) = authenticated(Role.write).async { implicit request =>
    caseSrv.delete(id)
      .map(_ => NoContent)
  }

  @Timed
  def find() = authenticated(Role.read).async(fieldsBodyParser) { implicit request =>
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val nparent = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (cases, total) = caseSrv.find(query, range, sort)
    val casesWithStats = auxSrv.apply(cases, nparent, withStats)
    renderer.toOutput(OK, casesWithStats, total)
  }

  @Timed
  def stats() = authenticated(Role.read).async(fieldsBodyParser) { implicit request =>
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val aggs = request.body.getValue("stats").getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Seq[Agg]]
    caseSrv.stats(query, aggs).map(s => Ok(s))
  }

  @Timed
  def linkedCases(id: String) = authenticated(Role.read).async { implicit request =>
    caseSrv.linkedCases(id)
      .runWith(Sink.seq)
      .map { cases =>
        val casesList = cases.sortWith {
          case ((c1, _), (c2, _)) => c1.startDate().after(c2.startDate())
        }.map {
          case (caze, artifacts) =>
            Json.toJson(caze).as[JsObject] - "description" +
              ("linkedWith" -> Json.toJson(artifacts)) +
              ("linksCount" -> Json.toJson(artifacts.size))
        }
        renderer.toOutput(OK, casesList)
      }
  }

  @Timed
  def merge(caseId1: String, caseId2: String) = authenticated(Role.read).async { implicit request =>
    caseMergeSrv.merge(caseId1, caseId2).map { caze =>
      renderer.toOutput(OK, caze)
    }
  }
}