package connectors

import scala.collection.immutable

import play.api.libs.json.{ JsObject, Json }
import play.api.mvc._
import play.api.routing.sird.UrlContext
import play.api.routing.{ Router, SimpleRouter }

import com.google.inject.AbstractModule
import javax.inject.{ Inject, Singleton }
import models.HealthStatus
import net.codingwell.scalaguice.{ ScalaModule, ScalaMultibinder }

trait Connector {
  val name: String
  val router: Router
  def status: JsObject = Json.obj("enabled" → true)
  def health: HealthStatus.Type = HealthStatus.Ok
}

@Singleton
class ConnectorRouter @Inject() (
    connectors: immutable.Set[Connector],
    actionBuilder: DefaultActionBuilder) extends SimpleRouter {
  def get(connectorName: String): Option[Connector] = connectors.find(_.name == connectorName)

  def routes: PartialFunction[RequestHeader, Handler] = {
    case request @ p"/$connector/$path<.*>" ⇒
      get(connector)
        .flatMap(_.router.withPrefix(s"/$connector/").handlerFor(request))
        .getOrElse(actionBuilder { _ ⇒ Results.NotFound(s"connector $connector not found") })
  }
}

abstract class ConnectorModule extends AbstractModule with ScalaModule {
  def registerController[C <: Connector](implicit evidence: Manifest[C]): Unit = {
    val connectorBindings = ScalaMultibinder.newSetBinder[Connector](binder)
    connectorBindings.addBinding.to[C]
    ()
  }
}