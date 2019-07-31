package org.thp.cortex.dto.v0

import play.api.libs.json.{JsObject, Json, OFormat}

case class InputCortexAction(
    label: String,
    data: JsObject,
    dataType: String,
    tlp: Int,
    pap: Int,
    parameters: JsObject
)

object InputCortexAction {
  implicit val format: OFormat[InputCortexAction] = Json.format[InputCortexAction]
}
