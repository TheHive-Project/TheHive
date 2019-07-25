package org.thp.cortex.dto.v0

import java.util.Date

import play.api.libs.json._

import scala.util.Try

object CortexJobStatus extends Enumeration {
  type Type = Value
  val InProgress, Success, Failure, Waiting, Unknown, Deleted = Value
}

object CortexJobType extends Enumeration {
  type JobType = Value
  val unknown, analyzer, responder = Value
}

case class CortexInputJob(
    id: String,
    workerId: String,
    workerName: String,
    workerDefinition: String,
    date: Date
)

object CortexInputJob {
  implicit val format: OFormat[CortexInputJob] = Json.format[CortexInputJob]
}

case class CortexOutputJob(
    id: String,
    workerId: String,
    workerName: String,
    workerDefinition: String,
    date: Date,
    startDate: Option[Date],
    endDate: Option[Date],
    status: CortexJobStatus.Type,
    data: Option[String],
    attachment: Option[JsObject],
    organization: String,
    dataType: String,
    attributes: JsObject,
    report: Option[CortexOutputReport],
    `type`: CortexJobType.JobType
)

case class CortexOutputAttachment(id: String, name: Option[String], contentType: Option[String])

object CortexOutputAttachment {
  implicit val format: Format[CortexOutputAttachment] = Json.format[CortexOutputAttachment]
}

case class CortexOutputArtifact(
    dataType: String,
    data: Option[String],
    attachment: Option[CortexOutputAttachment],
    message: Option[String],
    tlp: Int,
    tags: List[String]
)

object CortexOutputArtifact {
  implicit val format: Format[CortexOutputArtifact] = Json.format[CortexOutputArtifact]
}

case class CortexOutputReport(
    summary: JsObject,
    full: JsObject,
    success: Boolean,
    artifacts: List[CortexOutputArtifact],
    operations: List[CortexOutputOperation],
    errorMessage: Option[String],
    input: Option[String]
)

object CortexOutputReport {
  implicit val writes: Writes[CortexOutputReport] = Json.writes[CortexOutputReport]
  implicit val reads: Reads[CortexOutputReport] = Reads[CortexOutputReport](json => {
    if ((json \ "success").as[Boolean]) {
      for {
        summary <- (json \ "summary").validate[JsObject]
        full    <- (json \ "full").validate[JsObject]
        success <- (json \ "success").validate[Boolean]
        artifacts  = (json \ "artifacts").asOpt[List[CortexOutputArtifact]].getOrElse(Nil)
        operations = (json \ "operations").asOpt[List[CortexOutputOperation]].getOrElse(Nil)
      } yield CortexOutputReport(
        summary,
        full,
        success,
        artifacts,
        operations,
        None,
        None
      )
    } else {
      for {
        success <- (json \ "success").validate[Boolean]
        error = (json \ "errorMessage").asOpt[String]
        input = (json \ "input").asOpt[String]
      } yield CortexOutputReport(
        JsObject.empty,
        JsObject.empty,
        success,
        Nil,
        Nil,
        error,
        input
      )
    }
  })
}

object CortexOutputJob {
  private def filterObject(json: JsObject, attributes: String*): JsObject =
    JsObject(attributes.flatMap(a => (json \ a).asOpt[JsValue].map(a -> _)))

  implicit val writes: Writes[CortexOutputJob] = Json.writes[CortexOutputJob]
  implicit val cortexJobReads: Reads[CortexOutputJob] = Reads[CortexOutputJob](
    json =>
      for {
        id         <- (json \ "id").validate[String]
        analyzerId <- (json \ "workerId").orElse(json \ "analyzerId").validate[String]
        analyzerName       = (json \ "workerName").orElse(json \ "analyzerName").validate[String].getOrElse(analyzerId)
        analyzerDefinition = (json \ "workerDefinitionId").orElse(json \ "analyzerDefinitionId").validate[String].getOrElse(analyzerId)
        attributes         = (json \ "attributes").asOpt[JsObject].getOrElse(filterObject(json.as[JsObject], "tlp", "message", "parameters", "pap", "tpe"))
        data               = (json \ "data").asOpt[String]
        attachment         = (json \ "attachment").asOpt[JsObject]
        date <- (json \ "date").validate[Date]
        startDate = (json \ "startDate").asOpt[Date]
        endDate   = (json \ "endDate").asOpt[Date]
        status       <- (json \ "status").validate[String].map(s => Try(CortexJobStatus.withName(s)).getOrElse(CortexJobStatus.Unknown))
        organization <- (json \ "organization").validate[String]
        dataType     <- (json \ "dataType").validate[String]
        report = (json \ "report").asOpt[CortexOutputReport]
        jobType <- (json \ "type").validate[String].map(t => Try(CortexJobType.withName(t)).getOrElse(CortexJobType.unknown))
      } yield CortexOutputJob(
        id,
        analyzerId,
        analyzerName,
        analyzerDefinition,
        date,
        startDate,
        endDate,
        status,
        data,
        attachment,
        organization,
        dataType,
        attributes,
        report,
        jobType
      )
  )
}
