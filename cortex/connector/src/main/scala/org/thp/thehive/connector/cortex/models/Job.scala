package org.thp.thehive.connector.cortex.models

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity, EntityId}
import org.thp.thehive.models.{Observable, RichObservable}
import play.api.libs.json.{Format, JsObject, Json}

import java.util.Date

object JobStatus extends Enumeration {
  val InProgress, Success, Failure, Waiting, Deleted = Value

  implicit val format: Format[JobStatus.Value] = Json.formatEnum(JobStatus)
}

@BuildEdgeEntity[Observable, Job]
case class ObservableJob()

@BuildEdgeEntity[Job, Observable]
case class ReportObservable()

@BuildVertexEntity
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

case class RichJob(
    job: Job with Entity,
    observables: Seq[(RichObservable, JsObject)]
) {
  def _id: EntityId              = job._id
  def _createdBy: String         = job._createdBy
  def _updatedBy: Option[String] = job._updatedBy
  def _createdAt: Date           = job._createdAt
  def _updatedAt: Option[Date]   = job._updatedAt
  def workerId: String           = job.workerId
  def workerName: String         = job.workerName
  def workerDefinition: String   = job.workerDefinition
  def status: JobStatus.Value    = job.status
  def startDate: Date            = job.startDate
  def endDate: Date              = job.endDate
  def report: Option[JsObject]   = job.report
  def cortexId: String           = job.cortexId
  def cortexJobId: String        = job.cortexJobId

}
