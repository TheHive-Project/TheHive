package org.thp.thehive.connector.cortex

import play.api.routing.{Router ⇒ PlayRouter}
import play.api.{Configuration, Environment, Logger}

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.thp.scalligraph.query.QueryExecutor
import org.thp.thehive.connector.cortex.controllers.v0.{CortexQueryExecutor ⇒ CortexQueryExecutorV0}
import org.thp.thehive.connector.cortex.models.SchemaUpdater

class CortexModule(environment: Environment, configuration: Configuration) extends AbstractModule with ScalaModule {
  lazy val logger = Logger(getClass)

  override def configure(): Unit = {
    val routerBindings = ScalaMultibinder.newSetBinder[PlayRouter](binder)
    routerBindings.addBinding.toProvider[CortexRouter]
    val queryExecutorBindings = ScalaMultibinder.newSetBinder[QueryExecutor](binder)
    queryExecutorBindings.addBinding.to[CortexQueryExecutorV0]

    bind(classOf[SchemaUpdater]).asEagerSingleton()
    ()
  }
}
