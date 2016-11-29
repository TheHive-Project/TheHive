package controllers

import javax.inject.{ Inject, Singleton }

import scala.collection.immutable

import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{ Action, Controller }

import org.elastic4play.Timed
import org.elasticsearch.Build

import com.sksamuel.elastic4s.ElasticDsl

import connectors.Connector
import models.Case
import play.api.libs.json.JsObject
import org.elastic4play.services.auth.MultiAuthSrv
import play.api.libs.json.JsString
import org.elastic4play.services.AuthSrv

@Singleton
class StatusCtrl @Inject() (
    connectors: immutable.Set[Connector],
    authSrv: AuthSrv) extends Controller {

  private[controllers] def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  @Timed("controllers.StatusCtrl.get")
  def get = Action {
    Ok(Json.obj(
      "versions" → Json.obj(
        "TheHive" → getVersion(classOf[models.Case]),
        "Elastic4Play" → getVersion(classOf[Timed]),
        "Play" → getVersion(classOf[Controller]),
        "Elastic4s" → getVersion(classOf[ElasticDsl]),
        "ElasticSearch" → getVersion(classOf[org.elasticsearch.Build])),
      "connectors" → JsObject(connectors.map(c ⇒ c.name → Json.obj("enabled" → true)).toSeq),
      "config" → Json.obj(
        "authType" → (authSrv match {
          case multiAuthSrv: MultiAuthSrv ⇒ multiAuthSrv.authProviders.map { a ⇒ JsString(a.name) }
          case _                          ⇒ JsString(authSrv.name)
        }),
        "capabilities" → authSrv.capabilities.map(c ⇒ JsString(c.toString)))))
  }
}