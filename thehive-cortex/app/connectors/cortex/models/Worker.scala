package connectors.cortex.models

case class Responder(
    id: String,
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    maxTlp: Option[Long],
    maxPap: Option[Long],
    cortexIds: List[String] = Nil
) {

  def addCortexId(cid: String): Responder = copy(cortexIds = cid :: cortexIds)

  def join(responder: Responder): Responder = copy(cortexIds = cortexIds ::: responder.cortexIds)
}
