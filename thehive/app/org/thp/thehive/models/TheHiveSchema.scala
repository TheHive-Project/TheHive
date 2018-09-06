package org.thp.thehive.models

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Model.{Edge, Vertex}
import org.thp.scalligraph.models.{Database, Schema}
import org.thp.thehive.services._

@Singleton
class TheHiveSchema @Inject()(
    val caseSrv: CaseSrv,
    val userSrv: UserSrv,
    val taskSrv: TaskSrv,
    val logSrv: LogSrv,
    val impactStatusSrv: ImpactStatusSrv,
    val customFieldSrv: CustomFieldSrv,
    val localUserSrv: LocalUserSrv,
    val organisationSrv: OrganisationSrv
)(implicit db: Database)
    extends Schema {
  val auditModel: Vertex[Audit]                   = db.getVertexModel[Audit]
  val auditedModel: Edge[Audited, Audit, Product] = Audited.model

}
