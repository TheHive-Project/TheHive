package controllers

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.ElasticDsl
import connectors.Connector
import javax.inject.{Inject, Singleton}
import models.HealthStatus
import org.elastic4play.Timed
import org.elastic4play.database.DBIndex
import org.elastic4play.services.AuthSrv
import org.elastic4play.services.auth.MultiAuthSrv
import org.elasticsearch.client.Node
import play.api.Configuration
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsBoolean, JsObject, JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Try

@Singleton
class StatusCtrl @Inject()(
    connectors: immutable.Set[Connector],
    configuration: Configuration,
    dbIndex: DBIndex,
    authSrv: AuthSrv,
    system: ActorSystem,
    components: ControllerComponents,
    implicit val ec: ExecutionContext
) extends AbstractController(components) {

  private[controllers] def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")
  private var clusterStatusName: String            = "Init"
  val checkStatusInterval: FiniteDuration          = configuration.getOptional[FiniteDuration]("statusCheckInterval").getOrElse(1.minute)
  private def updateStatus(): Unit = {
    clusterStatusName = Try(dbIndex.clusterStatusName).getOrElse("ERROR")
    system.scheduler.scheduleOnce(checkStatusInterval)(updateStatus())
    ()
  }
  updateStatus()

  @Timed("controllers.StatusCtrl.get")
  def get: Action[AnyContent] = Action {
    Ok(
      Json.obj(
        "versions" -> Json.obj(
          "TheHive"       -> getVersion(classOf[models.Case]),
          "Elastic4Play"  -> getVersion(classOf[Timed]),
          "Play"          -> getVersion(classOf[AbstractController]),
          "Elastic4s"     -> getVersion(classOf[ElasticDsl]),
          "ElasticSearch" -> getVersion(classOf[Node])
        ),
        "connectors" -> JsObject(connectors.map(c => c.name -> c.status).toSeq),
        "health"     -> Json.obj("elasticsearch" -> clusterStatusName),
        "config" -> Json.obj(
          "protectDownloadsWith" -> configuration.get[String]("datastore.attachment.password"),
          "authType" -> (authSrv match {
            case multiAuthSrv: MultiAuthSrv =>
              multiAuthSrv.authProviders.map { a =>
                JsString(a.name)
              }
            case _ => JsString(authSrv.name)
          }),
          "capabilities" -> authSrv.capabilities.map(c => JsString(c.toString)),
          "ssoAutoLogin" -> JsBoolean(configuration.getOptional[Boolean]("auth.sso.autologin").getOrElse(false))
        )
      )
    )
  }

  @Timed("controllers.StatusCtrl.health")
  def health: Action[AnyContent] = Action.async {
    for {
      dbStatusInt <- dbIndex.getClusterStatus
      dbStatus = dbStatusInt match {
        case 0 => HealthStatus.Ok
        case 1 => HealthStatus.Warning
        case _ => HealthStatus.Error
      }
      distinctStatus = connectors.map(c => c.health) + dbStatus
      globalStatus = if (distinctStatus.contains(HealthStatus.Ok)) {
        if (distinctStatus.size > 1) HealthStatus.Warning else HealthStatus.Ok
      } else if (distinctStatus.contains(HealthStatus.Error)) HealthStatus.Error
      else HealthStatus.Warning
    } yield Ok(globalStatus.toString)
  }
}
