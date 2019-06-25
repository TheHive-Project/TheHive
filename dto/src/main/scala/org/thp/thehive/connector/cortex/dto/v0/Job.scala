package org.thp.thehive.connector.cortex.dto.v0

import java.util.Date

import play.api.libs.json.{Json, OFormat}

case class InputJob(analyzerId: String, cortexId: Option[String], artifactId: String)

object InputJob {
  implicit val format: OFormat[InputJob] = Json.format[InputJob]
}

case class OutputJob(
    analyzerId: String,
    analyzerName: Option[String],
    analyzerDefinition: Option[String],
    status: String,
    startDate: Date,
    endDate: Option[Date],
    report: Option[String],
    cortexId: Option[String],
    cortexJobId: Option[String]
)

object OutputJob {
  implicit val format: OFormat[OutputJob] = Json.format[OutputJob]
}
