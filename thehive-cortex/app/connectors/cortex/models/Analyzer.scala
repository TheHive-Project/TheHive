package connectors.cortex.models

import connectors.cortex.services.CortexClient

trait CortexModel[O] { self ⇒
  def onCortex(cortexId: String): O
}

case class Analyzer(
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    cortexIds: List[String] = Nil) extends CortexModel[Analyzer] {
  def id = (name + "_" + version).replaceAll("\\.", "_")
  def onCortex(cortexId: String) = copy(cortexIds = cortexId :: cortexIds)
}