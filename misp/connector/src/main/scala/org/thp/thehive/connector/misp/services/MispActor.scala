package org.thp.thehive.connector.misp.services

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import org.thp.scalligraph.auth.UserSrv
import play.api.Logger

import javax.inject.{Inject, Named, Provider}
import scala.concurrent.Future
import scala.util.{Failure, Success}

sealed trait MispMessage
case object Synchro                            extends MispMessage
case class EndOfSynchro(error: Option[String]) extends MispMessage

class MispActor @Inject() (
    connector: Connector,
    mispImportSrv: MispImportSrv,
    userSrv: UserSrv
) extends Actor {
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
    case EndOfSynchro(None) =>
      logger.info("MISP synchronisation is complete")
      context.become(waiting(context.system.scheduler.scheduleOnce(connector.syncInterval, self, Synchro)))
    case EndOfSynchro(Some(error)) =>
      logger.error(s"MISP synchronisation fails: $error")
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
        .onComplete {
          case _: Success[_] => self ! EndOfSynchro(None)
          case Failure(error) =>
            logger.error("MISP synchronisation failure", error)
            self ! EndOfSynchro(Some(error.toString))
        }
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
