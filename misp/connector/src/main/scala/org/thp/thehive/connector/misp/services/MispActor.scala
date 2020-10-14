package org.thp.thehive.connector.misp.services

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import javax.inject.{Inject, Named, Provider}
import org.thp.scalligraph.auth.UserSrv
import play.api.Logger

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object MispActor {
  case object Synchro
  case class EndOfSynchro(status: Try[Unit])
}

class MispActor @Inject() (
    connector: Connector,
    mispImportSrv: MispImportSrv,
    userSrv: UserSrv
) extends Actor {
  import MispActor._
  import context.dispatcher

  lazy val logger: Logger = Logger(getClass)

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"[$self] Starting actor MISP")
    context.become(waiting(context.system.scheduler.scheduleOnce(connector.syncInitialDelay, self, Synchro)))
  }

  override def receive: Receive = {
    case other => logger.warn(s"Unknown message $other (${other.getClass})")
  }

  def running: Receive = {
    case Synchro => logger.info("MISP synchronisation is already in progress")
    case EndOfSynchro(Success(_)) =>
      logger.info("MISP synchronisation is complete")
      context.become(waiting(context.system.scheduler.scheduleOnce(connector.syncInterval, self, Synchro)))
    case EndOfSynchro(Failure(error)) =>
      logger.error("MISP synchronisation fails", error)
      context.become(waiting(context.system.scheduler.scheduleOnce(connector.syncInterval, self, Synchro)))
    case other => logger.warn(s"Unknown message $other (${other.getClass})")
  }

  def waiting(scheduledSynchronisation: Cancellable): Receive = {
    case Synchro =>
      scheduledSynchronisation.cancel()
      context.become(running)
      logger.info(s"Synchronising MISP events for ${connector.clients.map(_.name).mkString(",")}")
      Future
        .traverse(connector.clients.filter(_.canImport))(mispImportSrv.syncMispEvents(_)(userSrv.getSystemAuthContext))
        .map(_ => ())
        .onComplete(status => self ! EndOfSynchro(status))
    case other => logger.warn(s"Unknown message $other (${other.getClass})")
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
