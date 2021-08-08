package org.thp.thehive.models.evolution

import org.thp.scalligraph.models.Operations
import org.thp.scalligraph.traversal.TraversalOps
import org.thp.thehive.models.ShareTask

import scala.util.Success

trait V4_0_3 extends TraversalOps {
  def evolutionV4_0_3: Operations => Operations =
    _.addProperty[Boolean]("ShareTask", "actionRequired")
      .updateGraphVertices("Add actionRequire property", "Share") { traversal =>
        traversal.outE[ShareTask].raw.property("actionRequired", false).iterate()
        Success(())
      }
}
