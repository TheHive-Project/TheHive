package org.thp.thehive.connector.cortex

import javax.inject.{Inject, Provider, Singleton}
import org.thp.thehive.connector.cortex.controllers.v0
import play.api.Logger
import play.api.routing.Router

@Singleton
class CortexRouter @Inject() (routerV0: v0.Router) extends Provider[Router] {

  lazy val logger: Logger = Logger(getClass)
  lazy val get: Router    =
    //routerV1.withPrefix("/api/cortex/v1/") orElse
    routerV0.withPrefix("/api/connector/cortex/")
}
