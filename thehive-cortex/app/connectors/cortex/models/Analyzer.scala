package connectors.cortex.models

case class Analyzer(
    id: String,
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    cortexIds: List[String] = Nil)