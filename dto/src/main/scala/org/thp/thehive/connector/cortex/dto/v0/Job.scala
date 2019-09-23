package org.thp.thehive.connector.cortex.dto.v0

import java.util.Date

import play.api.libs.json.{JsObject, Json, OFormat}

case class OutputJob(
    analyzerId: String,
    analyzerName: String,
    analyzerDefinition: String,
    status: String,
    startDate: Date,
    endDate: Date,
    report: Option[JsObject],
    cortexId: String,
    cortexJobId: String,
    id: String
)

object OutputJob {
  implicit val format: OFormat[OutputJob] = Json.format[OutputJob]
}
