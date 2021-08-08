package org.thp.thehive.models.evolution

import org.thp.scalligraph.EntityId
import org.thp.scalligraph.models.Operations
import org.thp.scalligraph.services.ElementOps
import org.thp.scalligraph.traversal.TraversalOps

import scala.util.Success

trait V4_1_5 extends TraversalOps with ElementOps {
  def evolutionV4_1_5: Operations => Operations =
    _.addProperty[EntityId]("Case", "owningOrganisation")
      .updateGraphVertices("Add owning organisation in case", "Case") { traversal =>
        traversal
          .project(
            _.by
              .by(_.in("ShareCase").unsafeHas("owner", true).in("OrganisationShare")._id.option)
          )
          .foreach {
            case (vertex, owningOrganisation) =>
              vertex.setProperty("owningOrganisation", owningOrganisation)
          }
        Success(())
      }
}
