package org.thp.thehive.connector.cortex.models

import java.util.Date

import play.api.libs.json.{Format, JsObject, Json}
import org.thp.scalligraph.{EdgeEntity, VertexEntity}
import org.thp.thehive.models.Observable

object JobStatus extends Enumeration {
  val InProgress, Success, Failure, Waiting, Deleted = Value

  implicit val format: Format[JobStatus.Value] = Json.formatEnum(JobStatus)
}

@EdgeEntity[Observable, Job]
case class ObservableJob()

@EdgeEntity[Job, Observable]
case class ReportObservable()

@VertexEntity
case class Job(
    workerId: String,
    workerName: String,
    workerDefinition: String,
    status: JobStatus.Value,
    startDate: Date,
    endDate: Date, // end date of the job or if it is not finished date of the last check
    report: Option[JsObject],
    cortexId: String,
    cortexJobId: String
)
