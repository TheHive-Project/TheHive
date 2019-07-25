package org.thp.thehive.connector.cortex.dto.v0

import java.util.Date

import play.api.libs.json.{JsObject, JsValue, Json, OFormat}

case class InputAction(
    responderId: String,
    responderName: Option[String],
    cortexId: Option[String],
    objectType: String,
    objectId: String,
    message: Option[String],
    parameters: Option[JsValue],
    tlp: Option[Int]
)

object InputAction {
  implicit val format: OFormat[InputAction] = Json.format[InputAction]
}

case class OutputAction(
    responderId: String,
    responderName: Option[String],
    responderDefinition: Option[String],
    cortexId: Option[String],
    cortexJobId: Option[String],
    objectType: String,
    objectId: String,
    status: String,
    startDate: Date,
    endDate: Option[Date],
    operations: String,
    report: String
)

object OutputAction {
  implicit val format: OFormat[OutputAction] = Json.format[OutputAction]
}
