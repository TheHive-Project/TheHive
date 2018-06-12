package connectors.cortex.models

case class Worker(
    id: String,
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    maxTlp: Option[Long],
    maxPap: Option[Long],
    cortexIds: List[String] = Nil) {

  def addCortexId(cid: String): Worker = copy(cortexIds = cid :: cortexIds)

  def join(worker: Worker): Worker = copy(cortexIds = cortexIds ::: worker.cortexIds)
}