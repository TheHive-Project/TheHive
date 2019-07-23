package org.thp.thehive.connector.cortex.dto.v0

import play.api.libs.json.{Json, OFormat}

case class InputResponder(
    name: String,
    description: String,
    dataTypeList: Seq[String]
)

object InputResponder {
  implicit val format: OFormat[InputResponder] = Json.format[InputResponder]
}

case class OutputResponder(
    id: String,
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    maxTlp: Option[Long],
    maxPap: Option[Long],
    cortexIds: List[String] = Nil
)

object OutputResponder {
  implicit val format: OFormat[OutputResponder] = Json.format[OutputResponder]
}
