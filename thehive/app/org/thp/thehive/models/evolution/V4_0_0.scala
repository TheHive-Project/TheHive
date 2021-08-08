package org.thp.thehive.models.evolution

import org.janusgraph.core.schema.ConsistencyModifier
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.Operations
import org.thp.scalligraph.traversal.TraversalOps
import org.thp.thehive.models.TheHiveSchemaDefinition.logger

import scala.util.{Success, Try}

trait V4_0_0 extends TraversalOps {
  def evolutionV4_0_0: Operations => Operations =
    _.addProperty[Option[Boolean]]("Observable", "seen")
      .updateGraphVertices("Add manageConfig permission to org-admin profile", "Profile") { traversal =>
        traversal.unsafeHas("name", "org-admin").raw.property("permissions", "manageConfig").iterate()
        Success(())
      }
      .updateGraphVertices("Remove duplicate custom fields", "CustomField") { traversal =>
        traversal.toIterator.foldLeft(Set.empty[String]) { (names, vertex) =>
          val name = vertex.value[String]("name")
          if (names.contains(name)) {
            vertex.remove()
            names
          } else
            names + name
        }
        Success(())
      }
      .noop // .addIndex("CustomField", IndexType.unique, "name")
      .dbOperation[JanusDatabase]("Remove locks") { db =>
        // removeIndexLock("CaseNumber")
        removePropertyLock(db, "number")
        // removeIndexLock("DataData")
        removePropertyLock(db, "data")
      }
      .noop // .addIndex("Tag", IndexType.unique, "namespace", "predicate", "value")
      .noop // .addIndex("Audit", IndexType.basic, "requestId", "mainAction")
      .rebuildIndexes

  private def removePropertyLock(db: JanusDatabase, name: String): Try[Unit] =
    db.managementTransaction { mgmt =>
      Try(mgmt.setConsistency(mgmt.getPropertyKey(name), ConsistencyModifier.DEFAULT))
        .recover {
          case error => logger.warn(s"Unable to remove lock on property $name: $error")
        }
    }
}
