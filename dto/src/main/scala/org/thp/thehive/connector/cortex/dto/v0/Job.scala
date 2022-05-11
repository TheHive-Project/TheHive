package org.thp.thehive.connector.cortex.dto.v0

import org.thp.thehive.dto.v0.OutputObservable
import play.api.libs.json.{JsObject, Json, OFormat}

import java.util.Date

case class OutputJob(
    _type: String,
    analyzerId: String,
    analyzerName: String,
    analyzerDefinition: String,
    status: String,
    startDate: Date,
    endDate: Date,
    report: Option[JsObject],
    cortexId: String,
    cortexJobId: String,
    id: String,
    case_artifact: Option[OutputObservable],
    operations: String
)

object OutputJob {
  implicit val format: OFormat[OutputJob] = Json.format[OutputJob]
}
