package org.thp.cortex.dto.v0

import play.api.libs.json.{Json, OFormat}

case class OutputReportTemplate(
    id: String,
    analyzerId: String,
    content: String,
    reportType: String
)

object OutputReportTemplate {
  implicit val format: OFormat[OutputReportTemplate] = Json.format[OutputReportTemplate]
}
