package controllers

import javax.inject.{ Inject, Singleton }

import scala.collection.immutable

import play.api.Configuration
import play.api.libs.json.{ JsObject, JsString, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{ AbstractController, ControllerComponents }

import com.sksamuel.elastic4s.ElasticDsl
import connectors.Connector

import org.elastic4play.Timed
import org.elastic4play.services.AuthSrv
import org.elastic4play.services.auth.MultiAuthSrv

@Singleton
class StatusCtrl @Inject() (
    connectors: immutable.Set[Connector],
    configuration: Configuration,
    authSrv: AuthSrv,
    components: ControllerComponents) extends AbstractController(components) {

  private[controllers] def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  @Timed("controllers.StatusCtrl.get")
  def get = Action {
    Ok(Json.obj(
      "versions" → Json.obj(
        "TheHive" → getVersion(classOf[models.Case]),
        "Elastic4Play" → getVersion(classOf[Timed]),
        "Play" → getVersion(classOf[AbstractController]),
        "Elastic4s" → getVersion(classOf[ElasticDsl]),
        "ElasticSearch" → getVersion(classOf[org.elasticsearch.Build])),
      "connectors" → JsObject(connectors.map(c ⇒ c.name → c.status).toSeq),
      "config" → Json.obj(
        "protectDownloadsWith" → configuration.get[String]("datastore.attachment.password"),
        "authType" → (authSrv match {
          case multiAuthSrv: MultiAuthSrv ⇒ multiAuthSrv.authProviders.map { a ⇒ JsString(a.name) }
          case _                          ⇒ JsString(authSrv.name)
        }),
        "capabilities" → authSrv.capabilities.map(c ⇒ JsString(c.toString)))))
  }
}
