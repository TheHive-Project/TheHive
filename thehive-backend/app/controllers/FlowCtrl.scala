package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext

import play.api.http.Status
import play.api.mvc._

import services.FlowSrv

import org.elastic4play.Timed
import org.elastic4play.controllers.{ Authenticated, Renderer }
import org.elastic4play.services.{ AuxSrv, Role }

@Singleton
class FlowCtrl @Inject() (
    flowSrv: FlowSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Status {

  /**
   * Return audit logs. For each item, include ancestor entities
   */
  @Timed
  def flow(rootId: Option[String], count: Option[Int]): Action[AnyContent] = authenticated(Role.read).async { implicit request ⇒
    val (audits, total) = flowSrv(rootId.filterNot(_ == "any"), count.getOrElse(10))
    renderer.toOutput(OK, audits, total)
  }
}