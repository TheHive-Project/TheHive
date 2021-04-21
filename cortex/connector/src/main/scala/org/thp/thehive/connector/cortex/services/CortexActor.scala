package org.thp.thehive.connector.cortex.services

import akka.actor._
import akka.pattern.pipe
import akka.stream.Materializer
import org.thp.client.ApplicationError
import org.thp.cortex.client.{CortexClient, CortexClientConfig}
import org.thp.cortex.dto.v0.{JobStatus, JobType, OutputJob}
import org.thp.scalligraph.EntityId
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import play.api.Logger

import java.util.Date
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

sealed trait CortexActorMessage
case class RemoteJob(job: OutputJob) extends CortexActorMessage
case class CheckJob(
    jobId: Option[EntityId],
    cortexJobId: String,
    actionId: Option[EntityId],
    cortexId: String,
    authContext: AuthContext
) extends CortexActorMessage

private case object CheckJobs extends CortexActorMessage
private case object CheckJobsKey
private case object FirstCheckJobs extends CortexActorMessage

sealed trait CortexTag

/**
  * This actor is primarily used to check Job statuses on regular
  * ticks using the provided client for each job
  */
class CortexActor(
    jobSrv: JobSrv,
    actionSrv: ActionSrv,
    applicationConfig: ApplicationConfig,
    materializer: Materializer,
    executionContext: ExecutionContext
) extends Actor
    with Timers {
  implicit val ec: ExecutionContext = context.dispatcher
  lazy val logger: Logger           = Logger(getClass)

  val clientsConfig: ConfigItem[Seq[CortexClientConfig], Seq[CortexClient]] =
    applicationConfig.mapItem[Seq[CortexClientConfig], Seq[CortexClient]](
      "cortex.servers",
      "",
      _.map(new CortexClient(_, materializer, executionContext))
    )
  val refreshDelayConfig: ConfigItem[FiniteDuration, FiniteDuration] = applicationConfig.item[FiniteDuration]("cortex.refreshDelay", "")
  val maxRetryOnErrorConfig: ConfigItem[Int, Int]                    = applicationConfig.item[Int]("cortex.maxRetryOnError", "")

  def clients: Seq[CortexClient]   = clientsConfig.get
  def refreshDelay: FiniteDuration = refreshDelayConfig.get
  def maxRetryOnError: Int         = maxRetryOnErrorConfig.get

  def receive: Receive = receive(Nil, 0)

  private def receive(checkedJobs: List[CheckJob], failuresCount: Int): Receive = {
    case FirstCheckJobs =>
      logger.debug(s"CortexActor starting check jobs ticking every $refreshDelay")
      timers.startTimerAtFixedRate(CheckJobsKey, CheckJobs, refreshDelay)

    case cj @ CheckJob(jobId, cortexJobId, actionId, cortexId, _) =>
      logger.info(s"CortexActor received job or action (${jobId.getOrElse(actionId.get)}, $cortexJobId, $cortexId) to check, added to $checkedJobs")
      if (!timers.isTimerActive(CheckJobsKey))
        timers.startSingleTimer(CheckJobsKey, FirstCheckJobs, 500.millis)
      context.become(receive(cj :: checkedJobs, failuresCount))

    case CheckJobs if checkedJobs.isEmpty =>
      logger.debug("CortexActor has empty checkedJobs state, stopping ticks")
      timers.cancel(CheckJobsKey)

    case CheckJobs =>
      checkedJobs
        .foreach {
          case CheckJob(_, cortexJobId, _, cortexId, _) =>
            clients
              .find(_.name == cortexId)
              .fold(logger.error(s"Receive a CheckJob for an unknown cortexId: $cortexId")) { client =>
                client
                  .getReport(cortexJobId, 1.second)
                  .recover { // this is a workaround for a timeout bug in Cortex
                    case ApplicationError(500, body) if (body \ "type").asOpt[String].contains("akka.pattern.AskTimeoutException") =>
                      OutputJob(cortexJobId, "", "", "", new Date, None, None, JobStatus.InProgress, None, None, "", "", None, JobType.analyzer)
                  }
                  .map(RemoteJob)
                  .pipeTo(self)
                ()
              }
        }

    case RemoteJob(job) if job.status == JobStatus.Success || job.status == JobStatus.Failure =>
      checkedJobs.find(_.cortexJobId == job.id) match {
        case Some(CheckJob(Some(jobId), cortexJobId, _, cortexId, authContext)) if job.`type` == JobType.analyzer =>
          logger.info(s"Job $cortexJobId in cortex $cortexId has finished with status ${job.status}, updating job $jobId")
          jobSrv.finished(cortexId, jobId, job)(authContext)
          context.become(receive(checkedJobs.filterNot(_.cortexJobId == job.id), failuresCount))

        case Some(CheckJob(_, cortexJobId, Some(actionId), cortexId, authContext)) if job.`type` == JobType.responder =>
          logger.info(s"Job $cortexJobId in cortex $cortexId has finished with status ${job.status}, updating action $actionId")
          actionSrv.finished(actionId, job)(authContext)
          context.become(receive(checkedJobs.filterNot(_.cortexJobId == job.id), failuresCount))

        case Some(_) =>
          logger.error(s"CortexActor received job output $job but with unknown type ${job.`type`}")

        case None =>
          logger.error(s"CortexActor received job output $job but did not have it in state $checkedJobs")
      }
    case RemoteJob(job) if job.status == JobStatus.InProgress || job.status == JobStatus.Waiting =>
      logger.info(s"CortexActor received ${job.status} from client, retrying in $refreshDelay")

    case _: RemoteJob =>
      logger.warn(s"CortexActor received JobStatus.Unknown from client, retrying in $refreshDelay")

    case Status.Failure(e) if failuresCount < maxRetryOnError =>
      logger.error(
        s"CortexActor received ${failuresCount + 1} failure(s), last: ${e.getMessage}, " +
          s"retrying again ${maxRetryOnError - failuresCount} time(s)"
      )
      context.become(receive(checkedJobs, failuresCount + 1))

    // TODO handle failure propagation on job or action side and 404 responses
    case Status.Failure(e) =>
      logger.error(s"CortexActor received $failuresCount failures, last: ${e.getMessage}, stopping ticks")
      timers.cancelAll()

    case x => logger.error(s"CortexActor received unhandled message ${x.toString}")
  }
}
