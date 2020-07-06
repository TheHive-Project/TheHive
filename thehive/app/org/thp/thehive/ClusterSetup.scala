package org.thp.thehive

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.google.inject.Injector
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Database
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}

import scala.concurrent.Future

@Singleton
class ClusterSetup @Inject() (
    configuration: Configuration,
    system: ActorSystem,
    applicationLifeCycle: ApplicationLifecycle,
    db: Database,
    injector: Injector
) {
  applicationLifeCycle
    .addStopHook(() => Future.successful(db.close()))
  if (configuration.get[Seq[String]]("akka.cluster.seed-nodes").isEmpty) {
    val logger: Logger = Logger(getClass)
    logger.info("Initialising cluster")
    val cluster = Cluster(system)
    cluster.join(cluster.system.provider.getDefaultAddress)
  }
  GuiceAkkaExtension(system).set(injector)
}
