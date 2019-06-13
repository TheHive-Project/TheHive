package org.thp.thehive.connector.cortex.controllers.v0

import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

import javax.inject.{Inject, Singleton}

@Singleton
class Router @Inject()(jobCtrl: JobCtrl) extends SimpleRouter {
  override def routes: Routes = {
    case GET(p"/job")          ⇒ jobCtrl.search
    case POST(p"/job/_search") ⇒ jobCtrl.search
  }
}
