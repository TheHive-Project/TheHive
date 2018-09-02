package org.thp.thehive.services

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.EdgeSrv
import org.thp.thehive.models.{Case, CaseUser, User}

@Singleton
class CaseUserSrv @Inject()(implicit db: Database) extends EdgeSrv[CaseUser, Case, User]
