package connectors.cortex.models

case class Analyzer(
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    cortexIds: List[String] = Nil) {
  def id = (name + "_" + version).replaceAll("\\.", "_")
}