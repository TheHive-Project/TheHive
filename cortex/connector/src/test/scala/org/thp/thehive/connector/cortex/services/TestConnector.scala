package org.thp.thehive.connector.cortex.services

import akka.actor.ActorSystem
import akka.stream.Materializer

import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexClient
import org.thp.scalligraph.services.config.ApplicationConfig
import org.thp.thehive.connector.cortex.models.CortexSchemaDefinition

import scala.concurrent.ExecutionContext

@Singleton
class TestConnector @Inject() (
    client: CortexClient,
    appConfig: ApplicationConfig,
    schemaDefinition: CortexSchemaDefinition,
    mat: Materializer,
    system: ActorSystem,
    ec: ExecutionContext
) extends Connector(appConfig, schemaDefinition, mat, system, ec) {
  override def clients: Seq[CortexClient] = Seq(client)

  override protected def updateHealth(): Unit = ()
  override protected def updateStatus(): Unit = ()
}
