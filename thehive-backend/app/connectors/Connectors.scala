package connectors

import javax.inject.Inject

import scala.collection.immutable

import play.api.libs.json.{ JsObject, Json }
import play.api.mvc.{ Action, Results }
import play.api.routing.{ Router, SimpleRouter }
import play.api.routing.sird.UrlContext

import com.google.inject.AbstractModule

import net.codingwell.scalaguice.{ ScalaModule, ScalaMultibinder }

trait Connector {
  val name: String
  val router: Router
  val status: JsObject = Json.obj("enabled" → true)
}

class ConnectorRouter @Inject() (connectors: immutable.Set[Connector]) extends SimpleRouter {
  def routes = {
    case request @ p"/$connector/$path<.*>" ⇒
      connectors
        .find(_.name == connector)
        .flatMap(_.router.withPrefix(s"/$connector/").handlerFor(request))
        .getOrElse(Action { _ ⇒ Results.NotFound(s"connector $connector not found") })
  }
}

abstract class ConnectorModule extends AbstractModule with ScalaModule {
  def registerController[C <: Connector](implicit evidence: Manifest[C]): Unit = {
    val connectorBindings = ScalaMultibinder.newSetBinder[Connector](binder)
    connectorBindings.addBinding.to[C]
    ()
  }
}