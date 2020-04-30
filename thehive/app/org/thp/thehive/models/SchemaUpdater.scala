package org.thp.thehive.models

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.models.{Database, Operations}
import org.thp.scalligraph.steps.StepsOps._
import play.api.Logger

import scala.util.Success

@Singleton
class SchemaUpdater @Inject() (theHiveSchema: TheHiveSchema, db: Database, userSrv: UserSrv) {
  lazy val logger: Logger = Logger(getClass)

  Operations("thehive", theHiveSchema)
    .forVersion(2)
    .addProperty[Option[Boolean]]("Observable", "seen")
    .updateGraph("Add manageConfig permission to org-admin profile", "Profile") { traversal =>
      traversal.has("name", "org-admin").raw.property(Key("permissions") -> "manageConfig").iterate()
      Success(())
    }
    .execute(db)(userSrv.getSystemAuthContext)
}
