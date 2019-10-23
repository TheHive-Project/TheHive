package org.thp.thehive.connector.cortex.dto.v0

import play.api.libs.json.{Json, OFormat}

case class OutputAnalyzerTemplate(
    id: String,
    analyzerId: String,
    content: String
)

object OutputAnalyzerTemplate {
  implicit val format: OFormat[OutputAnalyzerTemplate] = Json.format[OutputAnalyzerTemplate]
}

case class InputAnalyzerTemplate(
    analyzerId: String,
    content: String
)

object InputAnalyzerTemplate {
  implicit val format: OFormat[InputAnalyzerTemplate] = Json.format[InputAnalyzerTemplate]
}
