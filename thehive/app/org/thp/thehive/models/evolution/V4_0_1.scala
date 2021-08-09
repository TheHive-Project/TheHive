package org.thp.thehive.models.evolution

import org.thp.scalligraph.models.Operations
import org.thp.scalligraph.traversal.TraversalOps

import scala.util.Success

trait V4_0_1 extends TraversalOps {
  def evolutionV4_0_1: Operations => Operations =
    _.updateGraphVertices("Remove cases with a Deleted status", "Case") { traversal =>
      traversal.unsafeHas("status", "Deleted").remove()
      Success(())
    }
      .addProperty[Option[Boolean]]("Observable", "ignoreSimilarity")
}
