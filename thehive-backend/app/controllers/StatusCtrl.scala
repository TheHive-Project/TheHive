package controllers

import javax.inject.{ Inject, Singleton }

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import play.api.Configuration
import play.api.libs.json.{ JsObject, JsString, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{ AbstractController, Action, AnyContent, ControllerComponents }

import com.sksamuel.elastic4s.ElasticDsl
import connectors.Connector

import org.elastic4play.Timed
import org.elastic4play.database.DBIndex
import org.elastic4play.services.AuthSrv
import org.elastic4play.services.auth.MultiAuthSrv

@Singleton
class StatusCtrl @Inject() (
    connectors: immutable.Set[Connector],
    configuration: Configuration,
    dBIndex: DBIndex,
    authSrv: AuthSrv,
    components: ControllerComponents,
    implicit val ec: ExecutionContext) extends AbstractController(components) {

  private[controllers] def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  @Timed("controllers.StatusCtrl.get")
  def get: Action[AnyContent] = Action.async {
    val clusterStatusName = Try(dBIndex.clusterStatusName).getOrElse("ERROR")
    Future.traverse(connectors)(c ⇒ c.status.map(c.name → _))
      .map { connectorStatus ⇒
        Ok(Json.obj(
          "versions" → Json.obj(
            "TheHive" → getVersion(classOf[models.Case]),
            "Elastic4Play" → getVersion(classOf[Timed]),
            "Play" → getVersion(classOf[AbstractController]),
            "Elastic4s" → getVersion(classOf[ElasticDsl]),
            "ElasticSearch" → getVersion(classOf[org.elasticsearch.Build])),
          "connectors" → JsObject(connectorStatus.toSeq),
          "health" → Json.obj("elasticsearch" → clusterStatusName),
          "config" → Json.obj(
            "protectDownloadsWith" → configuration.get[String]("datastore.attachment.password"),
            "authType" → (authSrv match {
              case multiAuthSrv: MultiAuthSrv ⇒ multiAuthSrv.authProviders.map { a ⇒ JsString(a.name) }
              case _                          ⇒ JsString(authSrv.name)
            }),
            "capabilities" → authSrv.capabilities.map(c ⇒ JsString(c.toString)))))
      }
  }
}