package controllers

import javax.inject.{ Inject, Singleton }

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext

import play.api.http.Status
import play.api.mvc.Controller

import org.elastic4play.Timed
import org.elastic4play.controllers.{ Authenticated, Renderer }
import org.elastic4play.services.{ AuxSrv, Role }

import services.FlowSrv

@Singleton
class FlowCtrl @Inject() (
    flowSrv: FlowSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    implicit val ec: ExecutionContext) extends Controller with Status {

  /**
   * Return audit logs. For each item, include ancestor entities
   */
  @Timed
  def flow(rootId: Option[String], count: Option[Int]) = authenticated(Role.read).async { implicit request =>
    val (audits, total) = flowSrv(rootId.filterNot(_ == "any"), count.getOrElse(10))
    renderer.toOutput(OK, audits, total)
  }
}