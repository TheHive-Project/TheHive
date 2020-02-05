package org.thp.thehive.connector.cortex

import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.routing.{Router => PlayRouter}
import play.api.{Configuration, Environment, Logger}

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.thp.scalligraph.models.Schema
import org.thp.scalligraph.query.QueryExecutor
import org.thp.thehive.connector.cortex.controllers.v0.{CortexQueryExecutor => CortexQueryExecutorV0}
import org.thp.thehive.connector.cortex.models.{CortexSchema, SchemaUpdater}
import org.thp.thehive.connector.cortex.services.{Connector, CortexActor}
import org.thp.thehive.services.{Connector => TheHiveConnector}

class CortexModule(environment: Environment, configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
  lazy val logger: Logger = Logger(getClass)

  override def configure(): Unit = {
    val routerBindings = ScalaMultibinder.newSetBinder[PlayRouter](binder)
    routerBindings.addBinding.toProvider[CortexRouter]
    val queryExecutorBindings = ScalaMultibinder.newSetBinder[QueryExecutor](binder)
    queryExecutorBindings.addBinding.to[CortexQueryExecutorV0]
    val connectorBindings = ScalaMultibinder.newSetBinder[TheHiveConnector](binder)
    connectorBindings.addBinding.to[Connector]
    val schemaBindings = ScalaMultibinder.newSetBinder[Schema](binder)
    schemaBindings.addBinding.to[CortexSchema]

    bind[SchemaUpdater].asEagerSingleton()
    bindActor[CortexActor]("cortex-actor")
    ()
  }
}
