package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.ScalligraphApplicationLoader
import org.thp.scalligraph.auth.{AuthSrv, MultiAuthSrv}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.TheHiveModule
import org.thp.thehive.models.HealthStatus
import org.thp.thehive.services.{Connector, UserSrv}
import play.api.Configuration
import play.api.libs.json.{JsBoolean, JsObject, JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, Results}

import scala.collection.immutable
import scala.util.Success

@Singleton
class StatusCtrl @Inject()(
    entryPoint: EntryPoint,
    configuration: Configuration,
    authSrv: AuthSrv,
    userSrv: UserSrv,
    connectors: immutable.Set[Connector],
    db: Database
) {

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
              "protectDownloadsWith" -> configuration.get[String]("datastore.attachment.password"),
              "authType" -> (authSrv match {
                case multiAuthSrv: MultiAuthSrv =>
                  multiAuthSrv.authProviders.map { a =>
                    JsString(a.name)
                  }
                case _ => JsString(authSrv.name)
              }),
              "capabilities" -> authSrv.capabilities.map(c => JsString(c.toString)),
              "ssoAutoLogin" -> JsBoolean(configuration.get[Boolean]("auth.sso.autologin"))
            )
          )
        )
      )
    }

  private def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def health: Action[AnyContent] = entryPoint("health") { _ =>
    val dbStatus = db
      .tryTransaction(graph => userSrv.initSteps(graph).getByLogin("admin").getOrFail())
      .fold(_ => HealthStatus.Error, _ => HealthStatus.Ok)
    val connectorStatus = connectors.map(c => c.health)
    val distinctStatus  = connectorStatus + dbStatus
    val globalStatus = if (distinctStatus.contains(HealthStatus.Ok)) {
      if (distinctStatus.size > 1) HealthStatus.Warning else HealthStatus.Ok
    } else if (distinctStatus.contains(HealthStatus.Error)) HealthStatus.Error
    else HealthStatus.Warning

    Success(Results.Ok(Json.toJson(globalStatus)))
  }
}
