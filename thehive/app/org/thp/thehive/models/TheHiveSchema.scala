package org.thp.thehive.models

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.{Database, Schema}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.thehive.services._

@Singleton
class TheHiveSchema @Inject()(
    val caseSrv: CaseSrv,
    val userSrv: UserSrv,
    val taskSrv: TaskSrv,
    val logSrv: LogSrv,
    val impactStatusSrv: ImpactStatusSrv,
    val customFieldSrv: CustomFieldSrv,
    val caseCustomFieldSrv: CaseCustomFieldSrv,
    val localUserSrv: LocalUserSrv
)(implicit db: Database)
    extends Schema {
  val observableSrv       = new VertexSrv[Observable]
  val indicatorSrv        = new VertexSrv[Indicator]
  val caseImpactStatusSrv = new EdgeSrv[CaseImpactStatus, Case, ImpactStatus]
  val caseUserSrv         = new EdgeSrv[CaseUser, Case, User]
  val auditModel          = db.getVertexModel[Audit]
  val auditedModel        = Audited.model
}
