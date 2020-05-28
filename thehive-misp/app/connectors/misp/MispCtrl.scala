package connectors.misp

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import play.api.http.Status
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import play.api.routing.{Router, SimpleRouter}
import play.api.routing.sird.{GET, POST, UrlContext}
import play.api.{Configuration, Logger}
import akka.actor.ActorSystem
import connectors.Connector
import javax.inject.{Inject, Singleton}
import models.{HealthStatus, _}
import services.{AlertTransformer, CaseSrv}
import org.elastic4play.JsonFormat.tryWrites
import org.elastic4play.controllers.{Authenticated, Renderer}
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services._
import org.elastic4play.{NotFoundError, Timed}

@Singleton
class MispCtrl(
    checkStatusInterval: FiniteDuration,
    mispSynchro: MispSynchro,
    mispSrv: MispSrv,
    mispExport: MispExport,
    mispConfig: MispConfig,
    caseSrv: CaseSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    eventSrv: EventSrv,
    components: ControllerComponents,
    implicit val ec: ExecutionContext,
    implicit val system: ActorSystem
) extends AbstractController(components)
    with Connector
    with Status
    with AlertTransformer {

  @Inject()
  def this(
      configuration: Configuration,
      mispSynchro: MispSynchro,
      mispSrv: MispSrv,
      mispExport: MispExport,
      mispConfig: MispConfig,
      caseSrv: CaseSrv,
      authenticated: Authenticated,
      renderer: Renderer,
      eventSrv: EventSrv,
      components: ControllerComponents,
      ec: ExecutionContext,
      system: ActorSystem
  ) =
    this(
      configuration.getOptional[FiniteDuration]("misp.statusCheckInterval").getOrElse(1.minute),
      mispSynchro,
      mispSrv,
      mispExport,
      mispConfig,
      caseSrv,
      authenticated,
      renderer,
      eventSrv,
      components,
      ec,
      system
    )

  override val name: String = "misp"

  private var _status = JsObject.empty
  private def updateStatus(): Unit =
    Future
      .traverse(mispConfig.connections)(instance ⇒ instance.status())
      .onComplete {
        case Success(statusDetails) ⇒
          val distinctStatus = statusDetails.map(s ⇒ (s \ "status").as[String]).toSet
          val healthStatus = if (distinctStatus.contains("OK")) {
            if (distinctStatus.size > 1) "WARNING" else "OK"
          } else "ERROR"
          _status = Json.obj("enabled" → true, "servers" → statusDetails, "status" → healthStatus)
          system.scheduler.scheduleOnce(checkStatusInterval)(updateStatus())
        case _: Failure[_] ⇒
          _status = Json.obj("enabled" → true, "servers" → JsObject.empty, "status" → "ERROR")
          system.scheduler.scheduleOnce(checkStatusInterval)(updateStatus())
      }
  updateStatus()

  override def status: JsObject = _status

  private var _health: HealthStatus.Type = HealthStatus.Ok
  private def updateHealth(): Unit =
    Future
      .traverse(mispConfig.connections)(_.healthStatus())
      .onComplete {
        case Success(healthStatus) ⇒
          val distinctStatus = healthStatus.toSet
          _health = if (distinctStatus.contains(HealthStatus.Ok)) {
            if (distinctStatus.size > 1) HealthStatus.Warning else HealthStatus.Ok
          } else if (distinctStatus.contains(HealthStatus.Error)) HealthStatus.Error
          else HealthStatus.Warning
          system.scheduler.scheduleOnce(checkStatusInterval)(updateHealth())
        case _: Failure[_] ⇒
          _health = HealthStatus.Error
          system.scheduler.scheduleOnce(checkStatusInterval)(updateHealth())
      }
  updateHealth()

  override def health: HealthStatus.Type = _health

  private[MispCtrl] lazy val logger = Logger(getClass)

  val router: Router = SimpleRouter {
    case GET(p"/_syncAlerts")               ⇒ syncAlerts
    case GET(p"/_syncAllAlerts")            ⇒ syncAllAlerts
    case GET(p"/_syncArtifacts")            ⇒ syncArtifacts
    case POST(p"/export/$caseId/$mispName") ⇒ exportCase(mispName, caseId)
    case r                                  ⇒ throw NotFoundError(s"${r.uri} not found")
  }

  @Timed
  def syncAlerts: Action[AnyContent] = authenticated(Roles.admin).async { implicit request ⇒
    mispSynchro
      .synchronize()
      .map { m ⇒
        Ok(Json.toJson(m))
      }
  }

  @Timed
  def syncAllAlerts: Action[AnyContent] = authenticated(Roles.admin).async { implicit request ⇒
    mispSynchro
      .fullSynchronize()
      .map { m ⇒
        Ok(Json.toJson(m))
      }
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
      .flatMap { caze ⇒
        mispExport.export(mispName, caze)
      }
      .map {
        case (_, exportedAttributes) ⇒
          renderer.toMultiOutput(CREATED, exportedAttributes)
      }
  }

  override def createCase(alert: Alert, customCaseTemplate: Option[String])(implicit authContext: AuthContext): Future[Case] =
    mispSrv.createCase(alert, customCaseTemplate)

  override def mergeWithCase(alert: Alert, caze: Case)(implicit authContext: AuthContext): Future[Case] =
    mispSrv.mergeWithCase(alert, caze)
}
