package org.thp.thehive.services
import gremlin.scala.{Graph, GremlinScala, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.{BaseVertexSteps, Database}
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.models.KeyValue

@Singleton
class KeyValueSrv @Inject()()(implicit db: Database) extends VertexSrv[KeyValue, KeyValueSteps] {
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): KeyValueSteps = new KeyValueSteps(raw)
}

class KeyValueSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[KeyValue, KeyValueSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): KeyValueSteps = new KeyValueSteps(raw)
}
