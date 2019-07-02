package org.thp.thehive.connector.cortex.services

import akka.actor.{Timers, _}
import javax.inject.Inject
import org.thp.cortex.client.{CortexClient, CortexConfig}
import org.thp.cortex.dto.v0.CortexOutputJob
import org.thp.cortex.dto.v0.JobStatus

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Failure

object CortexActor {
  def props(): Props = Props[CortexActor]

  final case class CheckJob(jobId: String, cortexJobId: String, cortexClient: CortexClient)
  final private case class Job(id: String, cortexId: String)

  final private case class CheckedJobs(jobs: Map[Job, CortexClient])

  final private case object CheckJobs
  final private case object CheckJobsKey
  final private case object FirstCheckJobs
}

/**
  * This actor is primarily used to check Job statuses on regular
  * ticks using the provided client for each job
  */
class CortexActor @Inject()(cortexConfig: CortexConfig, jobSrv: JobSrv) extends Actor with Timers with ActorLogging {
  import CortexActor._
  import akka.pattern.pipe
  implicit val ec: ExecutionContext = context.dispatcher

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
          checkedJobs.copy(checkedJobs.jobs ++ Map(Job(jobId, cortexJobId) -> cortexClient))
        )
      )

    case CheckJobs =>
      checkedJobs
        .jobs
        .foreach(j => {
          j._2
            .getReport(j._1.cortexId, 0 second)
            .pipeTo(self)
        })
      if (checkedJobs.jobs.isEmpty) {
        log.debug("CortexActor has empty checkedJobs state, stopping ticks")
        timers.cancel(CheckJobsKey)
      }

    case j: CortexOutputJob =>
      j.status match {
        case s: JobStatus.Value if s == JobStatus.InProgress || s == JobStatus.Waiting =>
          log.info(s"CortexActor received ${j.status} from client, retrying in ${cortexConfig.refreshDelay}")

        case JobStatus.Unknown =>
          log.warning(s"CortexActor received JobStatus.Unknown from client, retrying in ${cortexConfig.refreshDelay}")

        case s: JobStatus.Value =>
          val job = checkedJobs.jobs.find(_._1.cortexId == j.id)
          if (job.nonEmpty) {
            log.info(s"Job ${j.id} in cortex ${job.map(_._2.name).getOrElse("unknown")} has finished with status $s, " +
              s"updating job ${job.map(_._1.id).getOrElse("unknown")}")
            jobSrv.checked(job.get._2, j)

            context.become(updated(checkedJobs.copy(checkedJobs.jobs - job.get._1)))
          } else {
            log.error(s"CortexActor received job output $j but did not have it in state ${checkedJobs.jobs}")
          }
      }

    case Failure(e) =>
      log.error(s"CortexActor received failure ${e.getMessage}, stopping ticks")
      timers.cancelAll()

    case x => log.error(s"CortexActor received unhandled message ${x.toString}")
  }
}
