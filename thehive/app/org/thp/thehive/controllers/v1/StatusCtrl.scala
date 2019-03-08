package org.thp.thehive.controllers.v1
import play.api.Configuration
import play.api.libs.json.{JsBoolean, JsObject, JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.{AuthSrv, MultiAuthSrv}
import org.thp.scalligraph.controllers.ApiMethod

@Singleton
class StatusCtrl @Inject()(apiMethod: ApiMethod, configuration: Configuration, authSrv: AuthSrv) {

  private def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def get: Action[AnyContent] =
    apiMethod("status") { _ ⇒
      Results.Ok(
        Json.obj(
          "versions" → Json.obj(
            "Scalligraph" → getVersion(classOf[org.thp.scalligraph.ScalligraphApplicationLoader]),
            "TheHive"     → getVersion(classOf[org.thp.thehive.TheHiveModule]),
            "Play"        → getVersion(classOf[AbstractController])
          ),
          "connectors" → JsObject.empty,
          "health"     → Json.obj("elasticsearch" → "UNKNOWN"),
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
        ))
    }

}
