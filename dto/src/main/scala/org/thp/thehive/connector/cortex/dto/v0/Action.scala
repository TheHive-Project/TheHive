package org.thp.thehive.connector.cortex.dto.v0

import play.api.libs.json.{JsObject, Json, OFormat}

case class InputAction(
    responderId: String,
    responderName: String,
    cortexId: Option[String],
    objectType: String,
    objectId: String,
    message: Option[String],
    parameters: Option[JsObject],
    tlp: Option[Int]
)

object InputAction {
  implicit val format: OFormat[InputAction] = Json.format[InputAction]
}
