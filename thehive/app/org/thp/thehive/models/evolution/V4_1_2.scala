package org.thp.thehive.models.evolution

import org.thp.scalligraph.models.Operations
import org.thp.scalligraph.services.ElementOps
import org.thp.scalligraph.traversal.TraversalOps

import scala.util.Success

trait V4_1_2 extends TraversalOps with ElementOps {
  def evolutionV4_1_2: Operations => Operations =
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
    _.noop
      .updateGraphVertices("Set taskId in logs", "Log") { traversal =>
        traversal
          .project(_.by.by(_.in("TaskLog")._id.option))
          .foreach {
            case (vertex, Some(taskId)) =>
              vertex.setProperty("taskId", taskId)
            case _ =>
          }
        Success(())
      }
}
