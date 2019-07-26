package org.thp.thehive.connector.cortex

import play.api.routing.{Router => PlayRouter}
import play.api.{Configuration, Environment, Logger}
import org.thp.thehive.services.{Connector => TheHiveConnector}
import com.google.inject.AbstractModule
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.thp.scalligraph.query.QueryExecutor
import org.thp.thehive.connector.cortex.controllers.v0.{CortexQueryExecutor => CortexQueryExecutorV0}
import org.thp.thehive.connector.cortex.models.SchemaUpdater
import play.api.libs.concurrent.AkkaGuiceSupport
import org.thp.thehive.connector.cortex.services.{Connector, CortexActor}

class CortexModule(environment: Environment, configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
  lazy val logger = Logger(getClass)

  override def configure(): Unit = {
    val routerBindings = ScalaMultibinder.newSetBinder[PlayRouter](binder)
    routerBindings.addBinding.toProvider[CortexRouter]
    val queryExecutorBindings = ScalaMultibinder.newSetBinder[QueryExecutor](binder)
    queryExecutorBindings.addBinding.to[CortexQueryExecutorV0]
    val connectorBindings = ScalaMultibinder.newSetBinder[TheHiveConnector](binder)
    connectorBindings.addBinding.to[Connector]

    bind(classOf[SchemaUpdater]).asEagerSingleton()
    bindActor[CortexActor]("cortex-actor")
    ()
  }
}
