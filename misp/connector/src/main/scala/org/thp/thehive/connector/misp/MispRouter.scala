package org.thp.thehive.connector.misp

import play.api.Logger
import play.api.mvc.DefaultActionBuilder
import play.api.routing.Router

import javax.inject.{Inject, Provider, Singleton}
import org.thp.thehive.connector.misp.controllers.v0

@Singleton
class MispRouter @Inject() (routerV0: v0.Router, actionBuilder: DefaultActionBuilder) extends Provider[Router] {

  lazy val logger = Logger(getClass)

  lazy val get: Router =
    //routerV1.withPrefix("/api/misp/v1/") orElse
    routerV0.withPrefix("/api/connector/misp/")
}
