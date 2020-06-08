package org.thp.thehive.models

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.janusgraph.core.schema.ConsistencyModifier
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{Database, IndexType, Operations}
import org.thp.scalligraph.steps.StepsOps._
import play.api.Logger
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future
import scala.util.{Success, Try}

@Singleton
class SchemaUpdater @Inject() (theHiveSchema: TheHiveSchema, db: Database, userSrv: UserSrv, applicationLifeCycle: ApplicationLifecycle) {
  lazy val logger: Logger = Logger(getClass)

  applicationLifeCycle.addStopHook(() => Future.successful(db.close()))

  Operations("thehive", theHiveSchema)
    .addProperty[Option[Boolean]]("Observable", "seen")
    .updateGraph("Add manageConfig permission to org-admin profile", "Profile") { traversal =>
      Try(traversal.has("name", "org-admin").raw.property(Key("permissions") -> "manageConfig").iterate())
      Success(())
    }
    .updateGraph("Remove duplicate custom fields", "CustomField") { traversal =>
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
    .addIndex("CustomField", IndexType.unique, "name")
    .dbOperation[JanusDatabase]("Remove locks") { db =>
      def removePropertyLock(name: String) =
        db.managementTransaction { mgmt =>
          Try(mgmt.setConsistency(mgmt.getPropertyKey(name), ConsistencyModifier.DEFAULT))
            .recover {
              case error => logger.warn(s"Unable to remove lock on property $name: $error")
            }
        }
      def removeIndexLock(name: String) =
        db.managementTransaction { mgmt =>
          Try(mgmt.setConsistency(mgmt.getGraphIndex(name), ConsistencyModifier.DEFAULT))
            .recover {
              case error => logger.warn(s"Unable to remove lock on index $name: $error")
            }
        }

      removeIndexLock("CaseNumber")
      removePropertyLock("number")
      removeIndexLock("DataData")
      removePropertyLock("data")
    }
    .addIndex("Tag", IndexType.tryUnique, "namespace", "predicate", "value")
    .dbOperation[JanusDatabase]("Enable indexes")(_.enableIndexes())
    .execute(db)(userSrv.getSystemAuthContext)
}
