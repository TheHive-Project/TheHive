package connectors.misp

import javax.inject.{ Inject, Singleton }

import connectors.Connector
import models.{ Alert, Case, UpdateMispAlertArtifact }
import org.elastic4play.JsonFormat.tryWrites
import org.elastic4play.controllers.Authenticated
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services._
import org.elastic4play.{ NotFoundError, Timed }
import play.api.Logger
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, Controller }
import play.api.routing.SimpleRouter
import play.api.routing.sird.{ GET, UrlContext }
import services.AlertTransformer

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class MispCtrl @Inject() (
    mispSrv: MispSrv,
    authenticated: Authenticated,
    eventSrv: EventSrv,
    implicit val ec: ExecutionContext) extends Controller with Connector with Status with AlertTransformer {

  override val name: String = "misp"

  private[MispCtrl] lazy val logger = Logger(getClass)
  val router = SimpleRouter {
    case GET(p"/_syncAlerts")    ⇒ syncAlerts
    case GET(p"/_syncAllAlerts") ⇒ syncAllAlerts
    case GET(p"/_syncArtifacts") ⇒ syncArtifacts
    case r                       ⇒ throw NotFoundError(s"${r.uri} not found")
  }

  @Timed
  def syncAlerts: Action[AnyContent] = authenticated(Role.admin).async { implicit request ⇒
    mispSrv.synchronize()
      .map { m ⇒ Ok(Json.toJson(m)) }
  }

  @Timed
  def syncAllAlerts: Action[AnyContent] = authenticated(Role.admin).async { implicit request ⇒
    mispSrv.fullSynchronize()
      .map { m ⇒ Ok(Json.toJson(m)) }
  }

  @Timed
  def syncArtifacts: Action[AnyContent] = authenticated(Role.admin) {
    eventSrv.publish(UpdateMispAlertArtifact())
    Ok("")
  }

  def createCase(alert: Alert)(implicit authContext: AuthContext): Future[Case] = {
    mispSrv.createCase(alert)
  }
}