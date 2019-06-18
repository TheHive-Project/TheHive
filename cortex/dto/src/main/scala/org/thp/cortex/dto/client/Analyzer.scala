package org.thp.cortex.dto.client

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
    cortexIds: List[String] = Nil
)

object OutputCortexAnalyzer {
  implicit val format: OFormat[OutputCortexAnalyzer] = Json.format[OutputCortexAnalyzer]
}
