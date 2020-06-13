package org.thp.thehive.connector.cortex.models

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.models.Database

@Singleton
class SchemaUpdater @Inject() (cortexSchema: CortexSchema, db: Database, userSrv: UserSrv) {
  cortexSchema.update(db)(userSrv.getSystemAuthContext).get
}
