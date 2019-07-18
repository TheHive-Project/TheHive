package org.thp.thehive.connector.cortex.services

import akka.actor.{Timers, _}
import javax.inject.Inject
import org.thp.cortex.client.{CortexClient, CortexConfig}
import org.thp.cortex.dto.v0.{CortexJobStatus, CortexJobType, CortexOutputJob}
import org.thp.scalligraph.auth.AuthContext

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object CortexActor {
  final case class CheckJob(jobId: String, cortexJobId: String, actionId: Option[String], cortexClient: CortexClient, authContext: AuthContext)

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

  def receive: Receive = receive(Nil, 0)

  private def receive(checkedJobs: List[CheckJob], failuresCount: Int): Receive = {
    case FirstCheckJobs =>
      log.debug(s"CortexActor starting check jobs ticking every ${cortexConfig.refreshDelay}")
      timers.startPeriodicTimer(CheckJobsKey, CheckJobs, cortexConfig.refreshDelay)

    case cj @ CheckJob(jobId, cortexJobId, _, cortexClient, _) =>
      log.info(s"CortexActor received job ($jobId, $cortexJobId, ${cortexClient.name}) to check, added to $checkedJobs")
      if (!timers.isTimerActive(CheckJobsKey)) {
        timers.startSingleTimer(CheckJobsKey, FirstCheckJobs, 500.millis)
      }
      context.become(receive(cj :: checkedJobs, failuresCount))

    case CheckJobs =>
      if (checkedJobs.isEmpty) {
        log.debug("CortexActor has empty checkedJobs state, stopping ticks")
        timers.cancel(CheckJobsKey)
      } else
        checkedJobs
          .foreach {
            case CheckJob(_, cortexJobId, _, cortexClient, _) =>
              cortexClient
                .getReport(cortexJobId, 1 second)
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
            case Some(job) if j.`type` == CortexJobType.analyzer =>
              log.info(
                s"Job ${j.id} in cortex ${job.cortexClient.name} has finished with status ${j.status}, " +
                  s"updating job ${job.jobId}"
              )
              if (j.status == CortexJobStatus.Failure) log.warning(s"Job ${j.id} has failed in Cortex")

              jobSrv.finished(job.jobId, j, job.cortexClient)(job.authContext)
              context.become(receive(checkedJobs.filterNot(_.cortexJobId == j.id), failuresCount))

            case Some(job) if j.`type` == CortexJobType.responder =>
              log.info(
                s"Job ${j.id} in cortex ${job.cortexClient.name} has finished with status ${j.status}, " +
                  s"updating action ${job.actionId.get}"
              )
              if (j.status == CortexJobStatus.Failure) log.warning(s"Job ${j.id} has failed in Cortex")

              // TODO
              context.become(receive(checkedJobs.filterNot(_.cortexJobId == j.id), failuresCount))

            case Some(_) =>
              log.error(s"CortexActor received job output $j but with unknown type ${j.`type`}")

            case None =>
              log.error(s"CortexActor received job output $j but did not have it in state $checkedJobs")
          }
      }

    case Status.Failure(e) if failuresCount < cortexConfig.maxRetryOnError =>
      log.error(
        s"CortexActor received ${failuresCount + 1} failure(s), last: ${e.getMessage}, " +
          s"retrying again ${cortexConfig.maxRetryOnError - failuresCount} time(s)"
      )
      context.become(receive(checkedJobs, failuresCount + 1))

    // TODO handle failure propagation on job or action side and 404 responses
    case Status.Failure(e) =>
      log.error(s"CortexActor received $failuresCount failures, last: ${e.getMessage}, stopping ticks")
      timers.cancelAll()

    case x => log.error(s"CortexActor received unhandled message ${x.toString}")
  }
}
