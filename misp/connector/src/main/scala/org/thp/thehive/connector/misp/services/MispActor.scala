package org.thp.thehive.connector.misp.services

import akka.actor.{Actor, Cancellable}
import org.thp.thehive.connector.misp.MispConnector
import play.api.Logger

sealed trait MispMessage
case object Synchro extends MispMessage

sealed trait MispTag
class MispActor(
    mispImportSrv: MispImportSrv
) extends Actor {
  import context.dispatcher

  lazy val logger: Logger = Logger(getClass)

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"[$self] Starting actor MISP")
    context.become(receive(context.system.scheduler.scheduleOnce(MispConnector.syncInitialDelay, self, Synchro)))
  }

  override def receive: Receive = {
    case other => logger.warn(s"Unknown message $other (${other.getClass})")
  }

  def receive(scheduledSynchronisation: Cancellable): Receive = {
    case Synchro =>
      scheduledSynchronisation.cancel()
      logger.info(s"Synchronising MISP events for ${MispConnector.clients.map(_.name).mkString(",")}")
      MispConnector.clients.filter(_.canImport).foreach { mispClient =>
        mispImportSrv.syncMispEvents(mispClient)
      }
      logger.info("MISP synchronisation is complete")
      context.become(receive(context.system.scheduler.scheduleOnce(MispConnector.syncInterval, self, Synchro)))
  }
}
