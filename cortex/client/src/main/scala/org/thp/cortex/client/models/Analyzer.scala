package org.thp.cortex.client.models

import play.api.libs.json.{Json, OFormat}

case class InputCortexAnalyzer(
    name: String,
    description: String,
    dataTypeList: Seq[String]
)

object InputCortexAnalyzer {
  implicit val format: OFormat[InputCortexAnalyzer] = Json.format[InputCortexAnalyzer]
}

case class OutputCortexAnalyzer(
    id: String,
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    cortexIds: Option[List[String]] = None
)

object OutputCortexAnalyzer {
  implicit val format: OFormat[OutputCortexAnalyzer] = Json.format[OutputCortexAnalyzer]
}
