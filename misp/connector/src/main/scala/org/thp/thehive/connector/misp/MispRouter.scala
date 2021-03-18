package org.thp.thehive.connector.misp

import org.thp.thehive.connector.misp.controllers.v0
import play.api.Logger
import play.api.routing.Router

import javax.inject.{Inject, Provider, Singleton}

@Singleton
class MispRouter @Inject() (routerV0: v0.Router) extends Provider[Router] {

  lazy val logger: Logger = Logger(getClass)

  lazy val get: Router =
    //routerV1.withPrefix("/api/misp/v1/") orElse
    routerV0.withPrefix("/api/connector/misp/")
}
