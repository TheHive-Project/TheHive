package org.thp.thehive.models.evolution

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality
import org.thp.scalligraph.models.Operations
import org.thp.scalligraph.traversal.TraversalOps

import scala.util.Success

trait V4_0_2 extends TraversalOps {
  def evolutionV4_0_2: Operations => Operations =
    _.updateGraphVertices("Add accessTheHiveFS permission to analyst and org-admin profiles", "Profile") { traversal =>
      traversal
        .unsafeHas("name", P.within("org-admin", "analyst"))
        .onRaw(
          _.property(Cardinality.set: Cardinality, "permissions", "accessTheHiveFS", Nil: _*)
        ) // Nil is for disambiguate the overloaded methods
        .iterate()
      Success(())
    }
}
