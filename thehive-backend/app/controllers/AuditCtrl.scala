package controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import play.api.http.Status
import play.api.mvc._

import models.Roles
import services.AuditSrv

import org.elastic4play.{BadRequestError, Timed}
import org.elastic4play.controllers.{Authenticated, Fields, FieldsBodyParser, Renderer}
import org.elastic4play.services.{Agg, AuxSrv, QueryDSL, QueryDef}
import org.elastic4play.services.JsonFormat.{aggReads, queryReads}

@Singleton
class AuditCtrl @Inject()(
    auditSrv: AuditSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext
) extends AbstractController(components)
    with Status {

  /**
    * Return audit logs. For each item, include ancestor entities
    */
  @Timed
  def flow(rootId: Option[String], count: Option[Int]): Action[AnyContent] = authenticated(Roles.read).async { _ =>
    val (audits, total) = auditSrv(rootId.filterNot(_ == "any"), count.getOrElse(10))
    renderer.toOutput(OK, audits, total)
  }

  @Timed
  def find(): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request =>
    val query     = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range     = request.body.getString("range")
    val sort      = request.body.getStrings("sort").getOrElse(Nil)
    val nparent   = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (alerts, total) = auditSrv.find(query, range, sort)
    val alertsWithStats = auxSrv.apply(alerts, nparent, withStats, removeUnaudited = false)
    renderer.toOutput(OK, alertsWithStats, total)
  }

  @Timed
  def stats(): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request =>
    val query = request
      .body
      .getValue("query")
      .fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val aggs = request
      .body
      .getValue("stats")
      .getOrElse(throw BadRequestError("Parameter \"stats\" is missing"))
      .as[Seq[Agg]]
    auditSrv.stats(query, aggs).map(s => Ok(s))
  }

}
