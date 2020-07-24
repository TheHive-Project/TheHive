package org.thp.thehive.connector.cortex.dto.v0

import java.util.Date

import org.thp.thehive.dto.v0.OutputObservable
import play.api.libs.json.{JsObject, Json, OFormat}

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
    case_artifact: Option[OutputObservable]
)

object OutputJob {
  implicit val format: OFormat[OutputJob] = Json.format[OutputJob]
}
