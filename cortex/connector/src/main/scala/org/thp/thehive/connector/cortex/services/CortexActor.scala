package org.thp.thehive.connector.cortex.services

import akka.actor._
import javax.inject._
import org.thp.cortex.client.CortexConfig
import play.api.Logger

object CortexActor {
  def props(): Props = Props[CortexActor]

  final case class CheckedJobs(jobs: Set[(String, String)])
  final case class CheckJob(jobId: String, cortexJobId: String)
}

class CortexActor @Inject()(cortexConfig: CortexConfig) extends Actor {
  import CortexActor._

  lazy val logger = Logger(getClass)

  def receive: Receive = updated(CheckedJobs(Set.empty))

  private def updated(checkedJobs: CheckedJobs): Receive = {
    case CheckJob(jobId, cortexJobId) =>
      logger.info(s"CortexActor received job ($jobId, $cortexJobId) to check, added to $checkedJobs")
      context.become(
        updated(
          checkedJobs.copy(checkedJobs.jobs ++ Set((jobId, cortexJobId)))
        )
      )
  }
}
