package org.thp.thehive.connector.cortex.services

import akka.actor.{Timers, _}
import javax.inject.Inject
import org.thp.cortex.client.{CortexClient, CortexConfig}

import scala.concurrent.duration._

object CortexActor {
  def props(): Props = Props[CortexActor]

  final case class CheckJob(jobId: String, cortexJobId: String, cortexClient: CortexClient)

  final private case class CheckedJobs(jobs: Map[(String, String), CortexClient])

  final private case object CheckJobs
  final private case object CheckJobsKey
  final private case object FirstCheckJobs
}

/**
  * This actor is primarily used to check Job statuses on regular
  * ticks using the provided client for each job
  */
class CortexActor @Inject()(cortexConfig: CortexConfig) extends Actor with Timers with ActorLogging {
  import CortexActor._

  def receive: Receive = updated(CheckedJobs(Map.empty))

  private def updated(checkedJobs: CheckedJobs): Receive = {
    case FirstCheckJobs =>
      log.debug(s"CortexActor starting check jobs ticking every ${cortexConfig.refreshDelay}")
      timers.startPeriodicTimer(CheckJobsKey, CheckJobs, cortexConfig.refreshDelay)

    case CheckJob(jobId, cortexJobId, cortexClient) =>
      log.info(s"CortexActor received job ($jobId, $cortexJobId, ${cortexClient.name}) to check, added to $checkedJobs")
      if (!timers.isTimerActive(CheckJobsKey)) {
        timers.startSingleTimer(CheckJobsKey, FirstCheckJobs, 500.millis)
      }
      context.become(
        updated(
          checkedJobs.copy(checkedJobs.jobs ++ Map((jobId, cortexJobId) -> cortexClient))
        )
      )

    case CheckJobs =>
      log.debug(s"CortexActor checking jobs $checkedJobs")

    case _ => log.error("CortexActor received unhandled message")
  }
}
