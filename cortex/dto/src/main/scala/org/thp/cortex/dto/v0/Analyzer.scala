package org.thp.cortex.dto.v0

import play.api.libs.json.{Json, OFormat}

case class InputAnalyzer(
    name: String,
    description: String,
    dataTypeList: Seq[String]
)

object InputAnalyzer {
  implicit val format: OFormat[InputAnalyzer] = Json.format[InputAnalyzer]
}

// FIXME add another model for output on client side or not?
case class OutputAnalyzer(
    id: String,
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    cortexIds: List[String] = Nil
)

object OutputAnalyzer {
  implicit val format: OFormat[OutputAnalyzer] = Json.format[OutputAnalyzer]
}
