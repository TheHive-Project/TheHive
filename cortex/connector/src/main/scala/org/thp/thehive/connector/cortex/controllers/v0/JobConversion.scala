package org.thp.thehive.connector.cortex.controllers.v0

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.cortex.dto.v0.{CortexJobStatus, CortexOutputJob}
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.connector.cortex.dto.v0.OutputJob
import org.thp.thehive.connector.cortex.models.{Job, JobStatus}
import org.thp.thehive.connector.cortex.services.JobSteps
import scala.language.implicitConversions

object JobConversion {

  implicit def toOutputJob(j: Job with Entity): Output[OutputJob] =
    Output[OutputJob](
      j.into[OutputJob]
        .withFieldComputed(_.analyzerId, _.workerId)
        .withFieldComputed(_.analyzerName, jb => Some(jb.workerName))
        .withFieldComputed(_.analyzerDefinition, jb => Some(jb.workerDefinition))
        .withFieldComputed(_.status, _.status.toString)
        .withFieldComputed(_.report, _.report.map(_.toString))
        .withFieldComputed(_.endDate, jb => Some(jb.endDate))
        .withFieldComputed(_.cortexId, jb => Some(jb.cortexId))
        .withFieldComputed(_.cortexJobId, jb => Some(jb.cortexJobId))
        .withFieldComputed(_.startDate, _.startDate)
        .transform
    )

  val jobProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[JobSteps]
      .property[String]("analyzerId")(_.rename("workerId").updatable)
      .property[Option[String]]("cortexId")(_.simple.updatable)
      .property[Date]("startDate")(_.simple.readonly)
      .build

  def fromCortexOutputJob(j: CortexOutputJob): Job =
    j.into[Job]
      .withFieldComputed(_.workerId, _.workerId)
      .withFieldComputed(_.workerName, _.workerName)
      .withFieldComputed(_.workerDefinition, _.workerDefinition)
      .withFieldComputed(_.status, s => JobStatus.withName(s.status.toString))
      .withFieldComputed(_.startDate, j => j.startDate.getOrElse(j.date))
      .withFieldComputed(_.endDate, j => j.endDate.getOrElse(j.date))
      .withFieldConst(_.report, None)
      .withFieldConst(_.cortexId, "tbd")
      .withFieldComputed(_.cortexJobId, _.id)
      .transform

  def fromCortexJobStatus(jobStatus: CortexJobStatus.Value): JobStatus.Value =
    jobStatus match {
      case CortexJobStatus.Failure    => JobStatus.Failure
      case CortexJobStatus.InProgress => JobStatus.InProgress
      case CortexJobStatus.Success    => JobStatus.Success
      case CortexJobStatus.Unknown    => JobStatus.Unknown
      case CortexJobStatus.Waiting    => JobStatus.Waiting
    }
}
