package org.thp.thehive

import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter

import javax.inject.Inject
import org.thp.thehive.controllers.v1

class Router @Inject()(routerV1: v1.Router) extends SimpleRouter {

  override def routes: Routes =
    routerV1.withPrefix("/api/v1/").routes orElse
      routerV1.withPrefix("/api/").routes // default version
}
