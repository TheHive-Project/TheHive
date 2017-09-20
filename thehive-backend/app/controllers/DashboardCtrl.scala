package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext

import play.api.Logger
import play.api.http.Status
import play.api.mvc._

import akka.stream.Materializer
import models.Roles
import services.DashboardSrv

import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.{ aggReads, queryReads }
import org.elastic4play.services._
import org.elastic4play.{ BadRequestError, Timed }

@Singleton
class DashboardCtrl @Inject() (
    dashboardSrv: DashboardSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) extends AbstractController(components) with Status {

  private[DashboardCtrl] lazy val logger = Logger(getClass)

  @Timed
  def create(): Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) { implicit request ⇒
    dashboardSrv.create(request.body)
      .map(dashboard ⇒ renderer.toOutput(CREATED, dashboard))
  }

  @Timed
  def get(id: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request ⇒
    dashboardSrv.get(id).map { dashboard ⇒
      renderer.toOutput(OK, dashboard)
    }
  }

  @Timed
  def update(id: String): Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) { implicit request ⇒
    dashboardSrv.update(id, request.body).map { dashboard ⇒
      renderer.toOutput(OK, dashboard)
    }
  }

  @Timed
  def delete(id: String): Action[AnyContent] = authenticated(Roles.write).async { implicit request ⇒
    dashboardSrv.delete(id)
      .map(_ ⇒ NoContent)
  }

  @Timed
  def find(): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)

    val (dashboards, total) = dashboardSrv.find(query, range, sort)
    renderer.toOutput(OK, dashboards, total)
  }

  @Timed
  def stats(): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val aggs = request.body.getValue("stats").getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Seq[Agg]]
    dashboardSrv.stats(query, aggs).map(s ⇒ Ok(s))
  }
}