package org.thp.cortex.dto.v0

import play.api.libs.json.{Json, OFormat}

case class OutputReportTemplate(
    id: String,
    analyzerId: String,
    content: String
)

object OutputReportTemplate {
  implicit val format: OFormat[OutputReportTemplate] = Json.format[OutputReportTemplate]
}

case class InputReportTemplate(
    analyzerId: String,
    content: String
)

object InputReportTemplate {
  implicit val format: OFormat[InputReportTemplate] = Json.format[InputReportTemplate]
}
