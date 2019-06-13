package org.thp.thehive.models

import play.api.Logger

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.models.Database

@Singleton
class SchemaUpdater @Inject()(theHiveSchema: TheHiveSchema, db: Database, userSrv: UserSrv) {
  val latestVersion: Int = 1

  val currentVersion: Int = db.version("thehive")
  if (currentVersion < latestVersion) {
    Logger(getClass).info(s"TheHive database schema is outdated ($currentVersion). Upgrading to version $latestVersion ...")
    db.createSchemaFrom(theHiveSchema)(userSrv.initialAuthContext)
    db.setVersion("thehive", latestVersion)
  }
}
