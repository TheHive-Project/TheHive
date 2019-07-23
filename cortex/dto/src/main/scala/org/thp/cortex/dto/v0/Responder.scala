package org.thp.cortex.dto.v0

import play.api.libs.json.{Json, OFormat}

case class OutputCortexResponder(
    id: String,
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    maxTlp: Option[Long],
    maxPap: Option[Long],
    cortexIds: Option[List[String]] = None
) {

  def addCortexId(cid: String): OutputCortexResponder = copy(cortexIds = cortexIds.map(l => cid :: l))

  def join(responder: OutputCortexResponder): OutputCortexResponder = copy(cortexIds = cortexIds.map(l => l ::: responder.cortexIds.getOrElse(Nil)))
}

object OutputCortexResponder {
  implicit val format: OFormat[OutputCortexResponder] = Json.format[OutputCortexResponder]
}

case class InputCortexResponder(responderName: String, responderId: String)

object InputCortexResponder {
  implicit val format: OFormat[InputCortexResponder] = Json.format[InputCortexResponder]
}
