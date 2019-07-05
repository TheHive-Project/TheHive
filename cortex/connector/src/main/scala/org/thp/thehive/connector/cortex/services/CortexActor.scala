package org.thp.thehive.connector.cortex.services

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Failure

import akka.actor.{Timers, _}
import javax.inject.Inject
import org.thp.cortex.client.{CortexClient, CortexConfig}
import org.thp.cortex.dto.v0.{CortexJobStatus, CortexOutputJob}
import org.thp.scalligraph.auth.AuthContext

object CortexActor {
  final case class CheckJob(jobId: String, cortexJobId: String, cortexClient: CortexClient, authContext: AuthContext)

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

  def receive: Receive = receive(Nil)

  private def receive(checkedJobs: List[CheckJob]): Receive = {
    case FirstCheckJobs =>
      log.debug(s"CortexActor starting check jobs ticking every ${cortexConfig.refreshDelay}")
      timers.startPeriodicTimer(CheckJobsKey, CheckJobs, cortexConfig.refreshDelay)

    case cj @ CheckJob(jobId, cortexJobId, cortexClient, _) =>
      log.info(s"CortexActor received job ($jobId, $cortexJobId, ${cortexClient.name}) to check, added to $checkedJobs")
      if (!timers.isTimerActive(CheckJobsKey)) {
        timers.startSingleTimer(CheckJobsKey, FirstCheckJobs, 500.millis)
      }
      context.become(receive(cj :: checkedJobs))

    case CheckJobs =>
      if (checkedJobs.isEmpty) {
        log.debug("CortexActor has empty checkedJobs state, stopping ticks")
        timers.cancel(CheckJobsKey)
      } else
        checkedJobs
          .foreach {
            case CheckJob(_, cortexJobId, cortexClient, _) =>
              cortexClient
                .getReport(cortexJobId, 0.second)
                .pipeTo(self)
          }

    case j: CortexOutputJob =>
      j.status match {
        case CortexJobStatus.InProgress | CortexJobStatus.Waiting =>
          log.info(s"CortexActor received ${j.status} from client, retrying in ${cortexConfig.refreshDelay}")

        case CortexJobStatus.Unknown =>
          log.warning(s"CortexActor received JobStatus.Unknown from client, retrying in ${cortexConfig.refreshDelay}")

        case CortexJobStatus.Success | CortexJobStatus.Failure =>
          checkedJobs.find(_.cortexJobId == j.id) match {
            case Some(job) =>
              log.info(
                s"Job ${j.id} in cortex ${job.cortexClient.name} has finished with status ${j.status}, " +
                  s"updating job ${job.jobId}"
              )
              jobSrv.finished(job.jobId, j, job.cortexClient)(job.authContext) // TODO add log if the job update fails
              context.become(receive(checkedJobs.filterNot(_.cortexJobId == j.id)))
            case None =>
              log.error(s"CortexActor received job output $j but did not have it in state $checkedJobs")
          }
      }

    case Failure(e) =>
      log.error(s"CortexActor received failure ${e.getMessage}, stopping ticks")
      timers.cancelAll()

    case x => log.error(s"CortexActor received unhandled message ${x.toString}")
  }
}
