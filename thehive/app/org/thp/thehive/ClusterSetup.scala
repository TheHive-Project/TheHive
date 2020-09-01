package org.thp.thehive

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.google.inject.Injector
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}

@Singleton
class ClusterSetup @Inject() (
    configuration: Configuration,
    system: ActorSystem,
    injector: Injector
) {
  if (configuration.get[Seq[String]]("akka.cluster.seed-nodes").isEmpty) {
    val logger: Logger = Logger(getClass)
    logger.info("Initialising cluster")
    val cluster = Cluster(system)
    cluster.join(cluster.system.provider.getDefaultAddress)
  }
  GuiceAkkaExtension(system).set(injector)
}
