package org.thp.thehive.connector.cortex.services

import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexClient
import org.thp.scalligraph.services.config.ApplicationConfig

@Singleton
class TestConnector @Inject()(client: CortexClient, appConfig: ApplicationConfig, mat: Materializer, system: ActorSystem)
    extends Connector(appConfig, mat, system) {
  override def clients: Seq[CortexClient] = Seq(client)

  override protected def updateHealth(): Unit = ()
  override protected def updateStatus(): Unit = ()
}
