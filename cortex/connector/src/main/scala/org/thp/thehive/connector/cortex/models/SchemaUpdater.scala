package org.thp.thehive.connector.cortex.models

import play.api.Logger

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.models.Database
import org.thp.thehive.models.{SchemaUpdater => TheHiveSchemaUpdater}

@Singleton
class SchemaUpdater @Inject()(thehiveSchemaUpdater: TheHiveSchemaUpdater, cortexSchema: CortexSchema, db: Database, userSrv: UserSrv) {
  val latestVersion: Int = 1

  val currentVersion: Int = db.version("cortex")
  if (currentVersion < latestVersion) {
    Logger(getClass).info(s"Cortex database schema is outdated ($currentVersion). Upgrading to version $latestVersion ...")
    db.createSchemaFrom(cortexSchema)(userSrv.getSystemAuthContext)
    db.setVersion("cortex", latestVersion)
  }
}
