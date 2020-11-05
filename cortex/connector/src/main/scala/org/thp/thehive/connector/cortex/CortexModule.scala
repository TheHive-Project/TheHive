package org.thp.thehive.connector.cortex

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.thp.scalligraph.models.{Database, Schema}
import org.thp.scalligraph.query.QueryExecutor
import org.thp.thehive.connector.cortex.controllers.v0.{CortexQueryExecutor => CortexQueryExecutorV0}
import org.thp.thehive.connector.cortex.models.{CortexSchemaDefinition, DatabaseProvider}
import org.thp.thehive.connector.cortex.services.notification.notifiers.{RunAnalyzerProvider, RunResponderProvider}
import org.thp.thehive.connector.cortex.services.{Connector, CortexActor}
import org.thp.thehive.services.notification.notifiers.NotifierProvider
import org.thp.thehive.services.{Connector => TheHiveConnector}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.routing.{Router => PlayRouter}
import play.api.{Configuration, Environment, Logger}

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
    schemaBindings.addBinding.to[CortexSchemaDefinition]

    val notifierBindings = ScalaMultibinder.newSetBinder[NotifierProvider](binder)
    notifierBindings.addBinding.to[RunResponderProvider]
    notifierBindings.addBinding.to[RunAnalyzerProvider]

    bind[Database].annotatedWithName("with-thehive-cortex-schema").toProvider[DatabaseProvider]
    bindActor[CortexActor]("cortex-actor")
    ()
  }
}
