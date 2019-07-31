package org.thp.cortex.dto.v0

import play.api.libs.json.{Json, OFormat}

case class OutputCortexWorker(
    id: String,
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    maxTlp: Long,
    maxPap: Long
)

object OutputCortexWorker {
  implicit val format: OFormat[OutputCortexWorker] = Json.format[OutputCortexWorker]
}

case class InputCortexWorker(id: String, name: String, description: String, dataTypeList: Seq[String])

object InputCortexWorker {
  implicit val format: OFormat[InputCortexWorker] = Json.format[InputCortexWorker]
}
