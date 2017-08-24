package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{ JsArray, JsObject, Json }
import play.api.mvc._

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import models.CaseStatus
import services.{ CaseMergeSrv, CaseSrv, CaseTemplateSrv, TaskSrv }

import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.{ aggReads, queryReads }
import org.elastic4play.services._
import org.elastic4play.{ BadRequestError, Timed }

@Singleton
class CaseCtrl @Inject() (
    caseSrv: CaseSrv,
    caseTemplateSrv: CaseTemplateSrv,
    caseMergeSrv: CaseMergeSrv,
    taskSrv: TaskSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) extends AbstractController(components) with Status {

  private[CaseCtrl] lazy val logger = Logger(getClass)

  @Timed
  def create(): Action[Fields] = authenticated(Role.write).async(fieldsBodyParser) { implicit request ⇒
    request.body
      .getString("template")
      .map { templateName ⇒
        caseTemplateSrv.getByName(templateName)
          .map(Some(_))
          .recover { case _ ⇒ None }
      }
      .getOrElse(Future.successful(None))
      .flatMap { caseTemplate ⇒
        caseSrv.create(request.body.unset("template"), caseTemplate)
      }
      .map(caze ⇒ renderer.toOutput(CREATED, caze))
  }

  @Timed
  def get(id: String): Action[AnyContent] = authenticated(Role.read).async { implicit request ⇒
    val withStats = for {
      statsValues ← request.queryString.get("nstats")
      firstValue ← statsValues.headOption
    } yield Try(firstValue.toBoolean).getOrElse(firstValue == "1")

    for {
      caze ← caseSrv.get(id)
      casesWithStats ← auxSrv.apply(caze, 0, withStats.getOrElse(false), removeUnaudited = false)
    } yield renderer.toOutput(OK, casesWithStats)
  }

  @Timed
  def update(id: String): Action[Fields] = authenticated(Role.write).async(fieldsBodyParser) { implicit request ⇒
    val isCaseClosing = request.body.getString("status").contains(CaseStatus.Resolved.toString)

    for {
      // Closing the case, so lets close the open tasks
      caze ← caseSrv.update(id, request.body)
      _ ← if (isCaseClosing) taskSrv.closeTasksOfCase(id) else Future.successful(Nil) // FIXME log warning if closedTasks contains errors
    } yield renderer.toOutput(OK, caze)
  }

  @Timed
  def bulkUpdate(): Action[Fields] = authenticated(Role.write).async(fieldsBodyParser) { implicit request ⇒
    val isCaseClosing = request.body.getString("status").contains(CaseStatus.Resolved.toString)

    request.body.getStrings("ids").fold(Future.successful(Ok(JsArray()))) { ids ⇒
      if (isCaseClosing) taskSrv.closeTasksOfCase(ids: _*) // FIXME log warning if closedTasks contains errors
      caseSrv.bulkUpdate(ids, request.body.unset("ids")).map(multiResult ⇒ renderer.toMultiOutput(OK, multiResult))
    }
  }

  @Timed
  def delete(id: String): Action[AnyContent] = authenticated(Role.write).async { implicit request ⇒
    caseSrv.delete(id)
      .map(_ ⇒ NoContent)
  }

  @Timed
  def find(): Action[Fields] = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val nparent = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (cases, total) = caseSrv.find(query, range, sort)
    val casesWithStats = auxSrv.apply(cases, nparent, withStats, removeUnaudited = false)
    renderer.toOutput(OK, casesWithStats, total)
  }

  @Timed
  def stats(): Action[Fields] = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val aggs = request.body.getValue("stats").getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Seq[Agg]]
    caseSrv.stats(query, aggs).map(s ⇒ Ok(s))
  }

  @Timed
  def linkedCases(id: String): Action[AnyContent] = authenticated(Role.read).async { implicit request ⇒
    caseSrv.linkedCases(id)
      .runWith(Sink.seq)
      .map { cases ⇒
        val casesList = cases.sortWith {
          case ((c1, _), (c2, _)) ⇒ c1.startDate().after(c2.startDate())
        }.map {
          case (caze, artifacts) ⇒
            Json.toJson(caze).as[JsObject] - "description" +
              ("linkedWith" → Json.toJson(artifacts)) +
              ("linksCount" → Json.toJson(artifacts.size))
        }
        renderer.toOutput(OK, casesList)
      }
  }

  @Timed
  def merge(caseId1: String, caseId2: String): Action[AnyContent] = authenticated(Role.read).async { implicit request ⇒
    caseMergeSrv.merge(caseId1, caseId2).map { caze ⇒
      renderer.toOutput(OK, caze)
    }
  }
}