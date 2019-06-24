package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.ScalligraphApplicationLoader
import org.thp.scalligraph.auth.{AuthSrv, MultiAuthSrv}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.TheHiveModule
import org.thp.thehive.models.HealthStatus
import org.thp.thehive.services.UserSrv
import play.api.Configuration
import play.api.libs.json.{JsBoolean, JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, Results}

import scala.util.{Success, Try}

@Singleton
class StatusCtrl @Inject()(entryPoint: EntryPoint, configuration: Configuration, authSrv: AuthSrv, userSrv: UserSrv, db: Database) {

  def get: Action[AnyContent] =
    entryPoint("status") { _ ⇒
      Success(
        Results.Ok(
          Json.obj(
            "versions" → Json.obj(
              "Scalligraph" → getVersion(classOf[ScalligraphApplicationLoader]),
              "TheHive"     → getVersion(classOf[TheHiveModule]),
              "Play"        → getVersion(classOf[AbstractController])
            ),
            "connectors" → Json.obj(
              "cortex" → Json.obj( // FIXME make this dynamic
                "enabled" → true,
                "servers" → List(
                  Json.obj(
                    "name"    → "interne",
                    "version" → "2.x.x",
                    "status"  → "OK"
                  )
                ),
                "status" → "OK"
              )
            ),
            "health" → Json.obj("elasticsearch" → "UNKNOWN"),
            "config" → Json.obj(
              "protectDownloadsWith" → configuration.get[String]("datastore.attachment.password"),
              "authType" → (authSrv match {
                case multiAuthSrv: MultiAuthSrv ⇒
                  multiAuthSrv.authProviders.map { a ⇒
                    JsString(a.name)
                  }
                case _ ⇒ JsString(authSrv.name)
              }),
              "capabilities" → authSrv.capabilities.map(c ⇒ JsString(c.toString)),
              "ssoAutoLogin" → JsBoolean(configuration.get[Boolean]("auth.sso.autologin"))
            )
          )
        )
      )
    }

  private def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def health: Action[AnyContent] = entryPoint("health") { _ ⇒
    // TODO add connectors and better db monitoring if available
    db.transaction(
      graph ⇒
        Try(userSrv.initSteps(graph).getByLogin("admin"))
          .map(_ ⇒ Results.Ok(HealthStatus.Ok.toString))
          .orElse(Success(Results.Ok(HealthStatus.Error.toString)))
    )
  }
}
