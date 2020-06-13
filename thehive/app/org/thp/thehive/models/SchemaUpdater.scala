package org.thp.thehive.models

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.models.Database
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

@Singleton
class SchemaUpdater @Inject() (theHiveSchema: TheHiveSchema, db: Database, userSrv: UserSrv, applicationLifeCycle: ApplicationLifecycle) {
  applicationLifeCycle
    .addStopHook(() => Future.successful(db.close()))
  theHiveSchema.operations.execute(db, theHiveSchema)(userSrv.getSystemAuthContext).get
}
