package org.thp.thehive.connector.misp.services

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import org.thp.scalligraph.auth.UserSrv
import play.api.Logger

import javax.inject.{Inject, Named, Provider}

sealed trait MispMessage
case object Synchro extends MispMessage

class MispActor @Inject() (
    connector: Connector,
    mispImportSrv: MispImportSrv
) extends Actor {
  import context.dispatcher

  lazy val logger: Logger = Logger(getClass)

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"[$self] Starting actor MISP")
    context.become(receive(context.system.scheduler.scheduleOnce(connector.syncInitialDelay, self, Synchro)))
  }

  override def receive: Receive = {
    case other => logger.warn(s"Unknown message $other (${other.getClass})")
  }

  def receive(scheduledSynchronisation: Cancellable): Receive = {
    case Synchro =>
      scheduledSynchronisation.cancel()
      logger.info(s"Synchronising MISP events for ${connector.clients.map(_.name).mkString(",")}")
      connector.clients.filter(_.canImport).foreach { mispClient =>
        mispImportSrv.syncMispEvents(mispClient)
      }
      logger.info("MISP synchronisation is complete")
      context.become(receive(context.system.scheduler.scheduleOnce(connector.syncInterval, self, Synchro)))
  }
}

class MispActorProvider @Inject() (system: ActorSystem, @Named("misp-actor-singleton") mispActorSingleton: ActorRef) extends Provider[ActorRef] {
  override def get(): ActorRef =
    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = mispActorSingleton.path.toStringWithoutAddress,
        settings = ClusterSingletonProxySettings(system)
      )
    )
}
