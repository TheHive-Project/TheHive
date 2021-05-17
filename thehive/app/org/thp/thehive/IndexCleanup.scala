package org.thp.thehive

import org.apache.tinkerpop.gremlin.structure.{Direction, Property}
import org.janusgraph.core.JanusGraph
import org.janusgraph.core.schema.{JanusGraphManagement, Parameter}
import org.janusgraph.graphdb.database.StandardJanusGraph
import org.janusgraph.graphdb.database.management.ManagementSystem
import org.janusgraph.graphdb.internal.JanusGraphSchemaCategory
import org.janusgraph.graphdb.types.TypeDefinitionDescription
import org.janusgraph.graphdb.types.system.BaseLabel
import org.thp.scalligraph.janus.JanusDatabase

import scala.jdk.CollectionConverters._

class IndexCleanup(db: JanusDatabase) {
  def propertyStr[A](property: Property[A]): String = {
    val p = property.asInstanceOf[Property[TypeDefinitionDescription]]
    def modStr(modifier: Any): String =
      modifier match {
        case a: Array[_]     => a.map(modStr).mkString("[", ",", "]")
        case p: Parameter[_] => s"${p.key()}=${p.value()}"
        case _               => modifier.toString
      }
    s"${p.key}=${p.value.getCategory}:${modStr(p.value.getModifier)}"
  }

  db.managementTransaction { mgmt =>
    val tx          = mgmt.asInstanceOf[ManagementSystem].getWrappedTx
    val indexVertex = tx.getSchemaVertex(JanusGraphSchemaCategory.GRAPHINDEX.getSchemaName("global"))
    indexVertex.remove()
    val edges = tx
      .query(indexVertex)
      .`type`(BaseLabel.SchemaDefinitionEdge)
      .direction(Direction.BOTH)
      .edges()
      .asScala
    edges
      .map(e => e.edgeLabel() + ": " + e.properties().asScala.map(propertyStr).mkString("<", " - ", ">"))
      .mkString("\n")
    indexVertex.remove()
    ???
  }
}
