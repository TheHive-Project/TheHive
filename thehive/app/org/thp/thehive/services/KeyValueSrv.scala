package org.thp.thehive.services

import scala.util.Try

import gremlin.scala.{Graph, GremlinScala, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.models.KeyValue

@Singleton
class KeyValueSrv @Inject()()(implicit db: Database) extends VertexSrv[KeyValue, KeyValueSteps] {
  def create(e: KeyValue)(implicit graph: Graph, authContext: AuthContext): Try[KeyValue with Entity] = createEntity(e)
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): KeyValueSteps                 = new KeyValueSteps(raw)
}

class KeyValueSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[KeyValue, KeyValueSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): KeyValueSteps = new KeyValueSteps(raw)
}
