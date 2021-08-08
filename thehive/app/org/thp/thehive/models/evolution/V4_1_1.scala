package org.thp.thehive.models.evolution

import org.thp.scalligraph.models.{IndexType, Operations}
import org.thp.scalligraph.traversal.TraversalOps

trait V4_1_1 extends TraversalOps {
  def evolutionV4_1_1: Operations => Operations =
    _.removeIndex("Alert", IndexType.fulltext, "description")
      .removeIndex("Case", IndexType.fulltext, "description", "summary")
      .removeIndex("Log", IndexType.fulltext, "message")
      .removeIndex("Observable", IndexType.fulltext, "message")
      .removeIndex("Log", IndexType.fulltext, "message")
      .removeIndex("Tag", IndexType.fulltext, "description")
      .removeIndex("Task", IndexType.fulltext, "description")
      //    .updateGraph("Set caseId in imported alerts", "Alert") { traversal =>
      //      traversal
      //        .project(
      //          _.by
      //            .by(_.out("AlertCase")._id.option)
      //        )
      //        .foreach {
      //          case (vertex, caseId) =>
      //            caseId.foreach(cid => vertex.property("caseId", cid.value))
      //        }
      //      Success(())
      //    }
      .noop
}
