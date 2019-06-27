package org.thp.cortex.dto.v0

import java.util.Date

import play.api.libs.json._

import scala.util.Try

object JobStatus extends Enumeration {
  type Type = Value
  val InProgress, Success, Failure, Waiting, Unknown = Value
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
    status: JobStatus.Type,
    data: Option[String],
    attachment: Option[JsObject],
    organization: String,
    dataType: String,
    attributes: JsObject
)

object CortexOutputJob {
  private def filterObject(json: JsObject, attributes: String*): JsObject =
    JsObject(attributes.flatMap(a ⇒ (json \ a).asOpt[JsValue].map(a → _)))

  implicit val writes: Writes[CortexOutputJob] = Json.writes[CortexOutputJob]
  implicit val cortexJobReads: Reads[CortexOutputJob] = Reads[CortexOutputJob](
    json ⇒
      for {
        id         ← (json \ "id").validate[String]
        analyzerId ← (json \ "workerId").orElse(json \ "analyzerId").validate[String]
        analyzerName       = (json \ "workerName").orElse(json \ "analyzerName").validate[String].getOrElse(analyzerId)
        analyzerDefinition = (json \ "workerDefinitionId").orElse(json \ "analyzerDefinitionId").validate[String].getOrElse(analyzerId)
        attributes         = filterObject(json.as[JsObject], "tlp", "message", "parameters", "pap", "tpe")
        data               = (json \ "data").asOpt[String]
        attachment         = (json \ "attachment").asOpt[JsObject]
        date         ← (json \ "date").validate[Date]
        startDate    = (json \ "startDate").asOpt[Date]
        endDate      = (json \ "endDate").asOpt[Date]
        status       ← (json \ "status").validate[String].map(s ⇒ Try(JobStatus.withName(s)).getOrElse(JobStatus.Unknown))
        organization ← (json \ "organization").validate[String]
        dataType     ← (json \ "dataType").validate[String]
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
        attributes
      )
  )
}
