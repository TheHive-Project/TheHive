package org.thp.thehive

import akka.actor.ActorSystem
import akka.cluster.Cluster
import javax.inject.Inject
import play.api.{Configuration, Logger}

class ClusterSetup @Inject() (configuration: Configuration, system: ActorSystem) {
  lazy val logger: Logger = Logger(getClass)
  if (configuration.get[Seq[String]]("akka.cluster.seed-nodes").isEmpty) {
    val cluster = Cluster(system)
    logger.info("Initialising cluster")
    cluster.join(cluster.system.provider.getDefaultAddress)
  }
}
