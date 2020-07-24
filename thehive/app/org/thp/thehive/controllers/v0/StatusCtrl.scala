package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.ScalligraphApplicationLoader
import org.thp.scalligraph.auth.{AuthCapability, AuthSrv, MultiAuthSrv}
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.TheHiveModule
import org.thp.thehive.models.{HealthStatus, User}
import org.thp.thehive.services.{Connector, UserSrv}
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, Results}

import scala.collection.immutable
import scala.util.Success

@Singleton
class StatusCtrl @Inject() (
    entrypoint: Entrypoint,
    appConfig: ApplicationConfig,
    authSrv: AuthSrv,
    userSrv: UserSrv,
    connectors: immutable.Set[Connector],
    @Named("with-thehive-schema") db: Database
) {

  val passwordConfig: ConfigItem[String, String] = appConfig.item[String]("datastore.attachment.password", "Password used to protect attachment ZIP")

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
              "capabilities" -> authSrv.capabilities.map(c => JsString(c.toString)),
              "ssoAutoLogin" -> authSrv.capabilities.contains(AuthCapability.sso)
            )
          )
        )
      )
    }

  def password: String = passwordConfig.get

  private def getVersion(c: Class[_]): String = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def health: Action[AnyContent] =
    entrypoint("health") { _ =>
      val dbStatus = db
        .roTransaction(graph => userSrv.getOrFail(User.system.login)(graph))
        .fold(_ => HealthStatus.Error, _ => HealthStatus.Ok)
      val connectorStatus = connectors.map(c => c.health)
      val distinctStatus  = connectorStatus + dbStatus
      val globalStatus = if (distinctStatus.contains(HealthStatus.Ok)) {
        if (distinctStatus.size > 1) HealthStatus.Warning else HealthStatus.Ok
      } else if (distinctStatus.contains(HealthStatus.Error)) HealthStatus.Error
      else HealthStatus.Warning

      Success(Results.Ok(globalStatus.toString))
    }
}
