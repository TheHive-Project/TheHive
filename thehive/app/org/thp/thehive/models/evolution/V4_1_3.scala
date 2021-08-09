package org.thp.thehive.models.evolution

import org.thp.scalligraph.EntityId
import org.thp.scalligraph.models.{IndexType, Operations}
import org.thp.scalligraph.services.ElementOps
import org.thp.scalligraph.traversal.TraversalOps

import scala.util.Success

trait V4_1_3 extends TraversalOps with ElementOps {
  def evolutionV4_1_3: Operations => Operations =
    _.removeIndex("Audit", IndexType.basic, "requestId", "mainAction")
      .removeIndex("Alert", IndexType.unique, "type", "source", "sourceRef", "organisationId")
      .removeIndex("_label_vertex_index", IndexType.basic)
      .removeIndex("Case", IndexType.basic, "status")
      .removeIndex("Task", IndexType.basic, "status")
      .removeIndex("Alert", IndexType.basic, "type", "source", "sourceRef")
      .updateGraphVertices("Set caseId in imported alerts", "Alert") { traversal =>
        traversal
          .project(
            _.by
              .by(_.out("AlertCase")._id.option)
          )
          .foreach {
            case (vertex, caseId) =>
              vertex.setProperty("caseId", caseId.getOrElse(EntityId.empty))
          }
        Success(())
      }
      .removeIndex("Alert", IndexType.standard)
      .removeIndex("Attachment", IndexType.standard)
      .removeIndex("Audit", IndexType.standard)
      .removeIndex("Case", IndexType.standard)
      .removeIndex("Log", IndexType.standard)
      .removeIndex("Observable", IndexType.standard)
      .removeIndex("Tag", IndexType.standard)
      .removeIndex("Task", IndexType.standard)
}
