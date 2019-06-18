package org.thp.thehive

import play.api.mvc._
import play.api.routing.sird._
import play.api.routing.{Router, SimpleRouter}
import play.api.{Environment, Logger, Mode}

import _root_.controllers.{Assets, ExternalAssets}
import com.google.inject.ProvidedBy
import javax.inject.{Inject, Provider, Singleton}
import org.thp.thehive.controllers.{v0, v1}

@Singleton
class TheHiveRouter @Inject()(routerV0: v0.Router, routerV1: v1.Router, assets: AssetGetter, actionBuilder: DefaultActionBuilder)
    extends Provider[Router] {

  lazy val logger = Logger(getClass)
  lazy val get: Router =
    SimpleRouter(
      routerV1.withPrefix("/api/v1/").routes orElse
        routerV0.withPrefix("/api/").routes orElse { // default version
        case GET(p"/")       ⇒ actionBuilder(Results.PermanentRedirect("/index.html"))
        case GET(p"/$file*") ⇒ assets.at(file)
      }
    )
}

@ProvidedBy(classOf[AssetProvider])
class AssetGetter @Inject()(get: String ⇒ Action[AnyContent]) {
  def at(name: String): Action[AnyContent] = get(name)
}

class AssetProvider @Inject()(environment: Environment, assets: Assets, extAssets: ExternalAssets) extends Provider[AssetGetter] {
  lazy val logger = Logger(getClass)

  val devResolver: String ⇒ Action[AnyContent] = {
    case name if name.startsWith("bower_components") ⇒ extAssets.at("frontend", name)
    case name                                        ⇒ extAssets.at("frontend/app", name)
  }

  def prodResolver: String ⇒ Action[AnyContent] = name ⇒ assets.at("/frontend", name)

  override def get(): AssetGetter = environment.mode match {
    case Mode.Dev ⇒ new AssetGetter(devResolver)
    case _        ⇒ new AssetGetter(prodResolver)
  }
}
