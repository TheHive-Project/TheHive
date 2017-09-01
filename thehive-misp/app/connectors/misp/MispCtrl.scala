package connectors.misp

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{ JsObject, Json }
import play.api.mvc._
import play.api.routing.SimpleRouter
import play.api.routing.sird.{ GET, UrlContext }

import connectors.Connector
import models.{ Alert, Case, Roles, UpdateMispAlertArtifact }
import services.{ AlertTransformer, CaseSrv }

import org.elastic4play.JsonFormat.tryWrites
import org.elastic4play.controllers.{ Authenticated, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services._
import org.elastic4play.{ NotFoundError, Timed }

@Singleton
class MispCtrl @Inject() (
    mispSynchro: MispSynchro,
    mispSrv: MispSrv,
    mispExport: MispExport,
    mispConfig: MispConfig,
    caseSrv: CaseSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    eventSrv: EventSrv,
    components: ControllerComponents,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Connector with Status with AlertTransformer {

  override val name: String = "misp"

  override val status: JsObject = Json.obj("enabled" → true, "servers" → mispConfig.connections.map(_.name))

  private[MispCtrl] lazy val logger = Logger(getClass)
  val router = SimpleRouter {
    case GET(p"/_syncAlerts")                 ⇒ syncAlerts
    case GET(p"/_syncAllAlerts")              ⇒ syncAllAlerts
    case GET(p"/_syncArtifacts")              ⇒ syncArtifacts
    case GET(p"/export/$caseId/to/$mispName") ⇒ exportCase(mispName, caseId)
    case r                                    ⇒ throw NotFoundError(s"${r.uri} not found")
  }

  @Timed
  def syncAlerts: Action[AnyContent] = authenticated(Roles.admin).async { implicit request ⇒
    mispSynchro.synchronize()
      .map { m ⇒ Ok(Json.toJson(m)) }
  }

  @Timed
  def syncAllAlerts: Action[AnyContent] = authenticated(Roles.admin).async { implicit request ⇒
    mispSynchro.fullSynchronize()
      .map { m ⇒ Ok(Json.toJson(m)) }
  }

  @Timed
  def syncArtifacts: Action[AnyContent] = authenticated(Roles.admin) {
    eventSrv.publish(UpdateMispAlertArtifact())
    Ok("")
  }

  @Timed
  def exportCase(mispName: String, caseId: String): Action[AnyContent] = authenticated(Roles.write).async { implicit request ⇒
    caseSrv
      .get(caseId)
      .flatMap { caze ⇒ mispExport.export(mispName, caze) }
      .map {
        case (_, exportedAttributes) ⇒
          renderer.toMultiOutput(CREATED, exportedAttributes)
      }
  }

  override def createCase(alert: Alert, customCaseTemplate: Option[String])(implicit authContext: AuthContext): Future[Case] = {
    mispSrv.createCase(alert, customCaseTemplate)
  }

  override def mergeWithCase(alert: Alert, caze: Case)(implicit authContext: AuthContext): Future[Case] = {
    mispSrv.mergeWithCase(alert, caze)
  }
}