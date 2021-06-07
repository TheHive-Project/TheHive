package org.thp.cortex.dto.v0

import org.thp.scalligraph.InternalError
import play.api.libs.json._

import java.util.Date
import scala.util.Try

sealed trait JobStatus extends Product with Serializable
object JobStatus {
  def withName(name: String): JobStatus =
    name match {
      case "InProgress" => JobStatus.InProgress
      case "Success"    => JobStatus.Success
      case "Failure"    => JobStatus.Failure
      case "Waiting"    => JobStatus.Waiting
      case "Deleted"    => JobStatus.Deleted
      case other        => throw InternalError(s"Invalid JobStatus (found: $other, expected: InProgress, Success, Failure, Waiting or Deleted)")
    }
  final case object InProgress extends JobStatus
  final case object Success    extends JobStatus
  final case object Failure    extends JobStatus
  final case object Waiting    extends JobStatus
  final case object Deleted    extends JobStatus
}

sealed abstract class JobType extends Product with Serializable
object JobType {
  def withName(name: String): JobType =
    name match {
      case "analyzer"  => analyzer
      case "responder" => responder
      case other       => throw InternalError(s"Invalid JobType (found: $other, expected: analyzer or responder)")
    }
  final case object analyzer  extends JobType
  final case object responder extends JobType
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
    status: JobStatus,
    data: Option[String],
    attachment: Option[JsObject],
    organization: String,
    dataType: String,
    report: Option[OutputReport],
    `type`: JobType
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
  implicit val writes: OWrites[OutputReport] = OWrites[OutputReport] { outputReport =>
    Json.obj(
      "summary"      -> Json.obj("taxonomies" -> Json.toJson(outputReport.summary)),
      "full"         -> Json.toJson(outputReport.full),
      "success"      -> Json.toJson(outputReport.success),
      "artifacts"    -> Json.toJson(outputReport.artifacts),
      "operations"   -> Json.toJson(outputReport.operations),
      "errorMessage" -> Json.toJson(outputReport.errorMessage),
      "input"        -> Json.toJson(outputReport.input)
    )
  }
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
  implicit val jobStatusFormat: Format[JobStatus] = Format[JobStatus](
    Reads {
      case JsString(name) => Try(JobStatus.withName(name)).fold(t => JsError(t.getMessage), JsSuccess(_))
      case other          => JsError(s"Invalid json for JobStatus (found: $other, expected: JsString(InProgress/Success/Failure/Waiting/Deleted))")
    },
    Writes(s => JsString(s.toString))
  )
  implicit val jobTypeFormat: Format[JobType] = Format[JobType](
    Reads {
      case JsString(name) => Try(JobType.withName(name)).fold(t => JsError(t.getMessage), JsSuccess(_))
      case other          => JsError(s"Invalid json for JobType (found: $other, expected: JsString(analyzer/responder))")
    },
    Writes(s => JsString(s.toString))
  )
  implicit val writes: Writes[OutputJob] = Json.writes[OutputJob]
  implicit val reads: Reads[OutputJob] = Reads[OutputJob](json =>
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
      status       <- (json \ "status").validate[JobStatus]
      organization <- (json \ "organization").validate[String]
      dataType     <- (json \ "dataType").validate[String]
      report = (json \ "report").asOpt[OutputReport]
      jobType <- (json \ "type").validate[JobType]
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
