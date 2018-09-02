package org.thp.thehive.services
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.EdgeSrv
import org.thp.thehive.models.{Case, CaseCustomField, CustomField}

@Singleton
class CaseCustomFieldSrv @Inject()(implicit db: Database) extends EdgeSrv[CaseCustomField, Case, CustomField]
