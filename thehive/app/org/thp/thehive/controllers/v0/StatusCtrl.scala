package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.ScalligraphApplicationLoader
import org.thp.scalligraph.auth.{AuthCapability, AuthSrv, MultiAuthSrv}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.{ApplicationConfiguration, ConfigItem}
import org.thp.thehive.TheHiveModule
import org.thp.thehive.models.HealthStatus
import org.thp.thehive.services.{Connector, UserSrv}
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, Results}

import scala.collection.immutable
import scala.util.Success

@Singleton
class StatusCtrl @Inject()(
    entryPoint: EntryPoint,
    appConfig: ApplicationConfiguration,
    authSrv: AuthSrv,
    userSrv: UserSrv,
    connectors: immutable.Set[Connector],
    db: Database
) {

  val passwordConfig: ConfigItem[String] = appConfig.item[String]("datastore.attachment.password", "Password used to protect attachment ZIP")
  def password: String                   = passwordConfig.get

  def get: Action[AnyContent] =
    entryPoint("status") { _ =>
      Success(
        Results.Ok(
          Json.obj(
            "versions" -> Json.obj(
              "Scalligraph" -> getVersion(classOf[ScalligraphApplicationLoader]),
              "TheHive"     -> getVersion(classOf[TheHiveModule]),
              "Play"        -> getVersion(classOf[AbstractController])
            ),
            "connectors" -> JsObject(connectors.map(c => c.name -> c.status).toSeq),
            "health"     -> Json.obj("elasticsearch" -> "UNKNOWN"),
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

  private def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def health: Action[AnyContent] = entryPoint("health") { _ =>
    val dbStatus = db
      .roTransaction(graph => userSrv.getOrFail("admin")(graph))
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
