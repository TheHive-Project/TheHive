package org.thp.cortex.dto.v0

import java.util.Date

import play.api.libs.json.{JsObject, Json, OFormat}

case class CortexInputJob(
    id: String,
    workerId: String,
    workerName: String,
    workerDefinition: String,
    date: Date
)

object CortexInputJob {
  implicit val format: OFormat[CortexInputJob] = Json.format[CortexInputJob]
}

case class CortexOutputJob(
    data: String,
    dataType: String,
    tlp: Int,
    message: String,
    parameters: JsObject
)

object CortexOutputJob {
  implicit val format: OFormat[CortexOutputJob] = Json.format[CortexOutputJob]
}