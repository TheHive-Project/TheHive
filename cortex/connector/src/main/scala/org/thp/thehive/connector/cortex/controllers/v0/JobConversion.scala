package org.thp.thehive.connector.cortex.controllers.v0

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.connector.cortex.dto.v0.{InputJob, OutputJob}
import org.thp.thehive.connector.cortex.models.{Job, JobStatus}
import org.thp.thehive.connector.cortex.services.JobSteps

import scala.language.implicitConversions

trait JobConversion {

  implicit def fromInputTask(inputJob: InputJob): Job =
    inputJob
      .into[Job]
      .withFieldComputed(_.workerId, _.analyzerId)
      .withFieldConst(_.workerName, "tbd")
      .withFieldConst(_.workerDefinition, "tbd")
      .withFieldConst(_.status, JobStatus.InProgress)
      .withFieldConst(_.startDate, new Date)
      .withFieldConst(_.endDate, new Date)
      .withFieldConst(_.report, None)
      .withFieldComputed(_.cortexId, _.cortexId.getOrElse(""))
      .withFieldConst(_.cortexJobId, "tbd")
      .transform

  implicit def toOutputJob(j: Job with Entity): Output[OutputJob] =
    Output[OutputJob](
      j.into[OutputJob]
        .withFieldComputed(_.analyzerId, _.workerId)
        .withFieldComputed(_.analyzerName, jb ⇒ Some(jb.workerName))
        .withFieldComputed(_.analyzerDefinition, jb ⇒ Some(jb.workerDefinition))
        .withFieldComputed(_.status, _.status.toString)
        .withFieldComputed(_.report, _.report.map(_.toString))
        .withFieldComputed(_.endDate, jb ⇒ Some(jb.endDate))
        .withFieldComputed(_.cortexId, jb ⇒ Some(jb.cortexId))
        .withFieldComputed(_.cortexJobId, jb ⇒ Some(jb.cortexJobId))
        .withFieldComputed(_.startDate, _.startDate)
        .transform
    )

  val jobProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[JobSteps]
      .property[String]("analyzerId")(_.rename("workerId").updatable)
      .property[Option[String]]("cortexId")(_.simple.updatable)
      .property[Date]("startDate")(_.simple.readonly)
      .build
}
