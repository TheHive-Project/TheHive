package org.thp.thehive.controllers.v0

import org.thp.scalligraph.auth.{AuthCapability, AuthSrv, MultiAuthSrv}
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.{EntityName, ScalligraphApplicationLoader}
import org.thp.thehive.TheHiveModule
import org.thp.thehive.models.{HealthStatus, TheHiveSchemaDefinition, User}
import org.thp.thehive.services.{Connector, UserSrv}
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.util.Success

@Singleton
class StatusCtrl @Inject() (
    entrypoint: Entrypoint,
    appConfig: ApplicationConfig,
    authSrv: AuthSrv,
    userSrv: UserSrv,
    connectors: immutable.Set[Connector],
    theHiveSchemaDefinition: TheHiveSchemaDefinition,
    db: Database
) {

  val passwordConfig: ConfigItem[String, String] = appConfig.item[String]("datastore.attachment.password", "Password used to protect attachment ZIP")
  def password: String                           = passwordConfig.get
  val streamPollingDurationConfig: ConfigItem[FiniteDuration, FiniteDuration] =
    appConfig.item[FiniteDuration]("stream.longPolling.pollingDuration", "amount of time the UI have to wait before polling the stream")
  def streamPollingDuration: FiniteDuration = streamPollingDurationConfig.get

  private def getVersion(c: Class[_]): String = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def get: Action[AnyContent] =
    entrypoint("status") { _ =>
      Success(
        Results.Ok(
          Json.obj(
            "versions" -> Json.obj(
              "Scalligraph" -> getVersion(classOf[ScalligraphApplicationLoader]),
              "TheHive"     -> getVersion(classOf[TheHiveModule]),
              "Play"        -> getVersion(classOf[AbstractController])
            ),
            "connectors" -> JsObject(connectors.map(c => c.name -> c.status).toSeq),
            "config" -> Json.obj(
              "protectDownloadsWith" -> password,
              "authType" -> (authSrv match {
                case multiAuthSrv: MultiAuthSrv => Json.toJson(multiAuthSrv.providerNames)
                case _                          => JsString(authSrv.name)
              }),
              "capabilities"    -> authSrv.capabilities.map(c => JsString(c.toString)),
              "ssoAutoLogin"    -> authSrv.capabilities.contains(AuthCapability.sso),
              "pollingDuration" -> streamPollingDuration.toMillis
            ),
            "schemaStatus" -> (connectors.flatMap(_.schemaStatus) ++ theHiveSchemaDefinition.schemaStatus).map { schemaStatus =>
              Json.obj(
                "name"            -> schemaStatus.name,
                "currentVersion"  -> schemaStatus.currentVersion,
                "expectedVersion" -> schemaStatus.expectedVersion,
                "error"           -> schemaStatus.error.map(_.getMessage)
              )
            }
          )
        )
      )
    }

  def health: Action[AnyContent] =
    entrypoint("health") { _ =>
      val dbStatus = db
        .roTransaction(graph => userSrv.getOrFail(EntityName(User.system.login))(graph))
        .fold(_ => HealthStatus.Error, _ => HealthStatus.Ok)
      val connectorStatus = connectors.map(c => c.health)
      val distinctStatus  = connectorStatus + dbStatus
      val globalStatus =
        if (distinctStatus.contains(HealthStatus.Ok))
          if (distinctStatus.size > 1) HealthStatus.Warning else HealthStatus.Ok
        else if (distinctStatus.contains(HealthStatus.Error)) HealthStatus.Error
        else HealthStatus.Warning

      Success(Results.Ok(globalStatus.toString))
    }
}
