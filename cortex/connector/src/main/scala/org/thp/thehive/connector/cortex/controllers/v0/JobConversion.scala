package org.thp.thehive.connector.cortex.controllers.v0

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.cortex.dto.v0.{CortexJobStatus, CortexOutputJob}
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.{Entity, UniMapping}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.connector.cortex.dto.v0.OutputJob
import org.thp.thehive.connector.cortex.models.{Job, JobStatus}
import org.thp.thehive.connector.cortex.services.JobSteps

object JobConversion {

  implicit def toOutputJob(j: Job with Entity): Output[OutputJob] =
    Output[OutputJob](
      j.asInstanceOf[Job]
        .into[OutputJob]
        .withFieldComputed(_.analyzerId, _.workerId)
        .withFieldComputed(_.analyzerName, _.workerName)
        .withFieldComputed(_.analyzerDefinition, _.workerDefinition)
        .withFieldComputed(_.status, _.status.toString)
        .withFieldComputed(_.endDate, _.endDate)
        .withFieldComputed(_.cortexId, _.cortexId)
        .withFieldComputed(_.cortexJobId, _.cortexJobId)
        .withFieldConst(_.id, j._id)
        .transform
    )

  val jobProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[JobSteps]
      .property("analyzerId", UniMapping.string)(_.rename("workerId").readonly)
      .property("cortexId", UniMapping.string.optional)(_.simple.readonly)
      .property("startDate", UniMapping.date)(_.simple.readonly)
      .property("status", UniMapping.string)(_.simple.readonly)
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
