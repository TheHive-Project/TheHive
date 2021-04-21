package org.thp.thehive.connector.misp.services

import akka.actor.{Actor, Cancellable}
import com.softwaremill.tagging.@@
import org.thp.scalligraph.services.config.ConfigItem
import org.thp.thehive.connector.misp.{SyncInitialDelay, SyncInterval}
import play.api.Logger

import scala.concurrent.duration.FiniteDuration

sealed trait MispMessage
case object Synchro extends MispMessage

sealed trait MispTag
class MispActor(
    mispImportSrv: MispImportSrv,
    clientsConfig: ConfigItem[Seq[TheHiveMispClientConfig], Seq[TheHiveMispClient]],
    syncInitialDelayConfig: ConfigItem[FiniteDuration, FiniteDuration] @@ SyncInitialDelay,
    syncIntervalConfig: ConfigItem[FiniteDuration, FiniteDuration] @@ SyncInterval
) extends Actor {
  import context.dispatcher

  lazy val logger: Logger = Logger(getClass)

  def clients: Seq[TheHiveMispClient]  = clientsConfig.get
  def syncInitialDelay: FiniteDuration = syncInitialDelayConfig.get
  def syncInterval: FiniteDuration     = syncIntervalConfig.get

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"[$self] Starting actor MISP")
    context.become(receive(context.system.scheduler.scheduleOnce(syncInitialDelay, self, Synchro)))
  }

  override def receive: Receive = {
    case other => logger.warn(s"Unknown message $other (${other.getClass})")
  }

  def receive(scheduledSynchronisation: Cancellable): Receive = {
    case Synchro =>
      scheduledSynchronisation.cancel()
      logger.info(s"Synchronising MISP events for ${clients.map(_.name).mkString(",")}")
      clients.filter(_.canImport).foreach { mispClient =>
        mispImportSrv.syncMispEvents(mispClient)
      }
      logger.info("MISP synchronisation is complete")
      context.become(receive(context.system.scheduler.scheduleOnce(syncInterval, self, Synchro)))
  }
}
