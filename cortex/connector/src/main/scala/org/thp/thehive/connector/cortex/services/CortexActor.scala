package org.thp.thehive.connector.cortex.services

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import play.api.Logger

import akka.actor.{Timers, _}
import akka.pattern.pipe
import javax.inject.Inject
import org.thp.cortex.dto.v0.{CortexJobStatus, CortexJobType, CortexOutputJob}
import org.thp.scalligraph.auth.AuthContext

object CortexActor {
  final case class CheckJob(
      jobId: Option[String],
      cortexJobId: String,
      actionId: Option[String],
      cortexId: String,
      authContext: AuthContext
  )

  final private case object CheckJobs
  final private case object CheckJobsKey
  final private case object FirstCheckJobs
}

/**
  * This actor is primarily used to check Job statuses on regular
  * ticks using the provided client for each job
  */
class CortexActor @Inject()(connector: Connector, jobSrv: JobSrv, actionSrv: ActionSrv) extends Actor with Timers {
  import CortexActor._
  implicit val ec: ExecutionContext = context.dispatcher
  lazy val logger                   = Logger(getClass)

  def receive: Receive = receive(Nil, 0)

  private def receive(checkedJobs: List[CheckJob], failuresCount: Int): Receive = {
    case FirstCheckJobs =>
      logger.debug(s"CortexActor starting check jobs ticking every ${connector.refreshDelay}")
      timers.startPeriodicTimer(CheckJobsKey, CheckJobs, connector.refreshDelay)

    case cj @ CheckJob(jobId, cortexJobId, actionId, cortexId, _) =>
      logger.info(s"CortexActor received job or action (${jobId.getOrElse(actionId.get)}, $cortexJobId, $cortexId) to check, added to $checkedJobs")
      if (!timers.isTimerActive(CheckJobsKey)) {
        timers.startSingleTimer(CheckJobsKey, FirstCheckJobs, 500.millis)
      }
      context.become(receive(cj :: checkedJobs, failuresCount))

    case CheckJobs =>
      if (checkedJobs.isEmpty) {
        logger.debug("CortexActor has empty checkedJobs state, stopping ticks")
        timers.cancel(CheckJobsKey)
      } else
        checkedJobs
          .foreach {
            case CheckJob(_, cortexJobId, _, cortexId, _) =>
              connector
                .clients
                .find(_.name == cortexId)
                .fold(logger.error(s"Receive a CheckJob for an unknown cortexId: $cortexId")) { client =>
                  client.getReport(cortexJobId, 1.second).pipeTo(self)
                  ()
                }
          }

    case j: CortexOutputJob =>
      j.status match {
        case CortexJobStatus.InProgress | CortexJobStatus.Waiting =>
          logger.info(s"CortexActor received ${j.status} from client, retrying in ${connector.refreshDelay}")

        case CortexJobStatus.Unknown =>
          logger.warn(s"CortexActor received JobStatus.Unknown from client, retrying in ${connector.refreshDelay}")

        case CortexJobStatus.Success | CortexJobStatus.Failure =>
          checkedJobs.find(_.cortexJobId == j.id) match {
            case Some(job) if j.`type` == CortexJobType.analyzer =>
              logger.info(
                s"Job ${j.id} in cortex ${job.cortexId} has finished with status ${j.status}, " +
                  s"updating job ${job.jobId.get}"
              )
              if (j.status == CortexJobStatus.Failure) logger.warn(s"Job ${j.id} has failed in Cortex")

              jobSrv.finished(job.cortexId, job.jobId.get, j)(job.authContext)
              context.become(receive(checkedJobs.filterNot(_.cortexJobId == j.id), failuresCount))

            case Some(job) if j.`type` == CortexJobType.responder =>
              logger.info(
                s"Job ${j.id} in cortex ${job.cortexId} has finished with status ${j.status}, " +
                  s"updating action ${job.actionId.get}"
              )
              if (j.status == CortexJobStatus.Failure) logger.warn(s"Job ${j.id} has failed in Cortex")

              actionSrv.finished(job.actionId.get, j)(job.authContext)
              context.become(receive(checkedJobs.filterNot(_.cortexJobId == j.id), failuresCount))

            case Some(_) =>
              logger.error(s"CortexActor received job output $j but with unknown type ${j.`type`}")

            case None =>
              logger.error(s"CortexActor received job output $j but did not have it in state $checkedJobs")
          }
      }

    case Status.Failure(e) if failuresCount < connector.maxRetryOnError =>
      logger.error(
        s"CortexActor received ${failuresCount + 1} failure(s), last: ${e.getMessage}, " +
          s"retrying again ${connector.maxRetryOnError - failuresCount} time(s)"
      )
      context.become(receive(checkedJobs, failuresCount + 1))

    // TODO handle failure propagation on job or action side and 404 responses
    case Status.Failure(e) =>
      logger.error(s"CortexActor received $failuresCount failures, last: ${e.getMessage}, stopping ticks")
      timers.cancelAll()

    case x => logger.error(s"CortexActor received unhandled message ${x.toString}")
  }
}
