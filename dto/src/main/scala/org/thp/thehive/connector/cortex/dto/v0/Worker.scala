package org.thp.thehive.connector.cortex.dto.v0

import play.api.libs.json.{Json, OFormat}

case class OutputWorker(
    id: String,
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    cortexIds: Seq[String]
)

object OutputWorker {
  implicit val format: OFormat[OutputWorker] = Json.format[OutputWorker]
}
