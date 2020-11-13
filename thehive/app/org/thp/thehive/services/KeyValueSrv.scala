package org.thp.thehive.services

import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.models.KeyValue

import scala.util.Try

@Singleton
class KeyValueSrv @Inject() ()(implicit @Named("with-thehive-schema") db: Database) extends VertexSrv[KeyValue] {
  def create(e: KeyValue)(implicit graph: Graph, authContext: AuthContext): Try[KeyValue with Entity] = createEntity(e)
}
