package org.thp.cortex.dto.v0

import java.util.Date

import play.api.libs.json._

object JobStatus extends Enumeration {
  val InProgress, Success, Failure, Waiting, Deleted = Value
}

object JobType extends Enumeration {
  val analyzer, responder = Value
}

case class InputJob(
    id: String,
    workerId: String,
    workerName: String,
    workerDefinition: String,
    date: Date
)

object InputJob {
  implicit val format: OFormat[InputJob] = Json.format[InputJob]
}

case class OutputJob(
    id: String,
    workerId: String,
    workerName: String,
    workerDefinition: String,
    date: Date,
    startDate: Option[Date],
    endDate: Option[Date],
    status: JobStatus.Value,
    data: Option[String],
    attachment: Option[JsObject],
    organization: String,
    dataType: String,
    report: Option[OutputReport],
    `type`: JobType.Value
)

case class OutputAttachment(id: String, name: Option[String], contentType: Option[String])

object OutputAttachment {
  implicit val format: Format[OutputAttachment] = Json.format[OutputAttachment]
}

case class OutputArtifact(
    dataType: String,
    data: Option[String],
    attachment: Option[OutputAttachment],
    message: Option[String],
    tlp: Int,
    tags: Set[String]
)

object OutputArtifact {
  implicit val format: Format[OutputArtifact] = Json.format[OutputArtifact]
}

case class OutputMinireport(level: String, namespace: String, predicate: String, value: JsValue)

object OutputMinireport {
  implicit val format: Format[OutputMinireport] = Json.format[OutputMinireport]

}

case class OutputReport(
    summary: Seq[OutputMinireport],
    full: Option[JsObject],
    success: Boolean,
    artifacts: Seq[OutputArtifact],
    operations: Seq[JsObject],
    errorMessage: Option[String],
    input: Option[String]
)

object OutputReport {
  implicit val writes: Writes[OutputReport] = Json.writes[OutputReport]
  implicit val reads: Reads[OutputReport] = Reads[OutputReport] { json =>
    JsSuccess(
      OutputReport(
        (json \ "summary" \ "taxonomies").asOpt[Seq[OutputMinireport]].getOrElse(Nil),
        (json \ "full").asOpt[JsObject],
        (json \ "success").asOpt[Boolean].contains(true),
        (json \ "artifacts").asOpt[Seq[OutputArtifact]].getOrElse(Nil),
        (json \ "operations").asOpt[Seq[JsObject]].getOrElse(Nil),
        (json \ "errorMessage").asOpt[String],
        (json \ "input").asOpt[String]
      )
    )
  }
}

object OutputJob {
  implicit val jobStatusFormat: Format[JobStatus.Value] = Json.formatEnum(JobStatus)
  implicit val jobTypeFormat: Format[JobType.Value]     = Json.formatEnum(JobType)
  implicit val writes: Writes[OutputJob]                = Json.writes[OutputJob]
  implicit val reads: Reads[OutputJob] = Reads[OutputJob](
    json =>
      for {
        id       <- (json \ "id").validate[String]
        workerId <- (json \ "workerId").orElse(json \ "analyzerId").validate[String]
        workerName       = (json \ "workerName").orElse(json \ "analyzerName").validate[String].getOrElse(workerId)
        workerDefinition = (json \ "workerDefinitionId").orElse(json \ "analyzerDefinitionId").validate[String].getOrElse(workerId)
        data             = (json \ "data").asOpt[String]
        attachment       = (json \ "attachment").asOpt[JsObject]
        date <- (json \ "date").validate[Date]
        startDate = (json \ "startDate").asOpt[Date]
        endDate   = (json \ "endDate").asOpt[Date]
        status       <- (json \ "status").validate[JobStatus.Value]
        organization <- (json \ "organization").validate[String]
        dataType     <- (json \ "dataType").validate[String]
        report = (json \ "report").asOpt[OutputReport]
        jobType <- (json \ "type").validate[JobType.Value]
      } yield OutputJob(
        id,
        workerId,
        workerName,
        workerDefinition,
        date,
        startDate,
        endDate,
        status,
        data,
        attachment,
        organization,
        dataType,
        report,
        jobType
      )
  )
}
