package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.services.VertexSrv
import org.thp.scalligraph.traversal.Graph
import org.thp.thehive.models.KeyValue

import scala.util.Try

class KeyValueSrv() extends VertexSrv[KeyValue] {
  def create(e: KeyValue)(implicit graph: Graph, authContext: AuthContext): Try[KeyValue with Entity] = createEntity(e)
}
