package org.thp.thehive.connector.cortex.services

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import play.api.Logger

import akka.actor._
import akka.pattern.pipe
import javax.inject.Inject
import org.thp.cortex.dto.v0.{JobStatus, JobType, OutputJob => CortexJob}
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
class CortexActor @Inject() (connector: Connector, jobSrv: JobSrv, actionSrv: ActionSrv) extends Actor with Timers {
  import CortexActor._
  implicit val ec: ExecutionContext = context.dispatcher
  lazy val logger                   = Logger(getClass)

  def receive: Receive = receive(Nil, 0)

  private def receive(checkedJobs: List[CheckJob], failuresCount: Int): Receive = {
    case FirstCheckJobs =>
      logger.debug(s"CortexActor starting check jobs ticking every ${connector.refreshDelay}")
      timers.startTimerAtFixedRate(CheckJobsKey, CheckJobs, connector.refreshDelay)

    case cj @ CheckJob(jobId, cortexJobId, actionId, cortexId, _) =>
      logger.info(s"CortexActor received job or action (${jobId.getOrElse(actionId.get)}, $cortexJobId, $cortexId) to check, added to $checkedJobs")
      if (!timers.isTimerActive(CheckJobsKey)) {
        timers.startSingleTimer(CheckJobsKey, FirstCheckJobs, 500.millis)
      }
      context.become(receive(cj :: checkedJobs, failuresCount))

    case CheckJobs if checkedJobs.isEmpty =>
      logger.debug("CortexActor has empty checkedJobs state, stopping ticks")
      timers.cancel(CheckJobsKey)

    case CheckJobs =>
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

    case cortexJob: CortexJob if cortexJob.status == JobStatus.Success || cortexJob.status == JobStatus.Failure =>
      checkedJobs.find(_.cortexJobId == cortexJob.id) match {
        case Some(CheckJob(Some(jobId), cortexJobId, _, cortexId, authContext)) if cortexJob.`type` == JobType.analyzer =>
          logger.info(s"Job $cortexJobId in cortex $cortexId has finished with status ${cortexJob.status}, updating job $jobId")
          jobSrv.finished(cortexId, jobId, cortexJob)(authContext)
          context.become(receive(checkedJobs.filterNot(_.cortexJobId == cortexJob.id), failuresCount))

        case Some(CheckJob(_, cortexJobId, Some(actionId), cortexId, authContext)) if cortexJob.`type` == JobType.responder =>
          logger.info(s"Job $cortexJobId in cortex $cortexId has finished with status ${cortexJob.status}, updating action $actionId")
          actionSrv.finished(actionId, cortexJob)(authContext)
          context.become(receive(checkedJobs.filterNot(_.cortexJobId == cortexJob.id), failuresCount))

        case Some(_) =>
          logger.error(s"CortexActor received job output $cortexJob but with unknown type ${cortexJob.`type`}")

        case None =>
          logger.error(s"CortexActor received job output $cortexJob but did not have it in state $checkedJobs")
      }
    case cortexJob: CortexJob if cortexJob.status == JobStatus.InProgress || cortexJob.status == JobStatus.Waiting =>
      logger.info(s"CortexActor received ${cortexJob.status} from client, retrying in ${connector.refreshDelay}")

    case _: CortexJob =>
      logger.warn(s"CortexActor received JobStatus.Unknown from client, retrying in ${connector.refreshDelay}")

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
