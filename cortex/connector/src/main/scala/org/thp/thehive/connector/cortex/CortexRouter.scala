package org.thp.thehive.connector.cortex

import play.api.Logger
import play.api.mvc.DefaultActionBuilder
import play.api.routing.{Router, SimpleRouter}

import javax.inject.{Inject, Provider, Singleton}
import org.thp.thehive.connector.cortex.controllers.v0

@Singleton
class CortexRouter @Inject()(routerV0: v0.Router, actionBuilder: DefaultActionBuilder) extends Provider[Router] {

  lazy val logger = Logger(getClass)
  lazy val get: Router =
    SimpleRouter(
//      routerV1.withPrefix("/api/cortex/v1/").routes orElse
      routerV0.withPrefix("/api/connector/cortex/").routes
    )
}
