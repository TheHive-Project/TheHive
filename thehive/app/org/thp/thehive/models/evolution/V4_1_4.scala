package org.thp.thehive.models.evolution

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{IndexType, Operations}
import org.thp.scalligraph.traversal.TraversalOps

import scala.util.{Success, Try}

trait V4_1_4 extends TraversalOps {
  def evolutionV4_1_4: Operations => Operations =
    _.dbOperation[JanusDatabase]("Remove global index if ElasticSearch is used") { db =>
      db.managementTransaction(mgmt => Try(mgmt.get("index.search.backend"))).flatMap {
        case "elasticsearch" => db.removeIndex("global", IndexType.fulltext, Nil)
        case _               => Success(())
      }
    }
      .updateGraphVertices("Add manageProcedure permission to org-admin and analyst profiles", "Profile") { traversal =>
        traversal
          .unsafeHas("name", P.within("org-admin", "analyst"))
          .raw
          .property("permissions", "manageProcedure")
          .iterate()
        Success(())
      }
      .removeIndex("Data", IndexType.unique, "data")
}
