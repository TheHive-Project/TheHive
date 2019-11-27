package org.thp.cortex.dto.v0

import play.api.libs.json.{JsObject, Json, OFormat}

case class InputAction(
    label: String,
    data: JsObject,
    dataType: String,
    tlp: Int,
    pap: Int,
    parameters: JsObject
)

object InputAction {
  implicit val format: OFormat[InputAction] = Json.format[InputAction]
}
