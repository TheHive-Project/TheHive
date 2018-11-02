package org.thp.thehive.models

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Model.{Edge, Vertex}
import org.thp.scalligraph.models.{InitialValue, Model, Schema}
import org.thp.scalligraph.services.VertexSrv
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
) extends Schema {
  val auditModel: Vertex[Audit]                    = Model.vertex[Audit]
  val auditedModel: Edge[Audited, Audit, Product]  = Audited.model
  def vertexSrvList: Seq[VertexSrv[_, _]]          = Seq(caseSrv, userSrv, taskSrv, logSrv, impactStatusSrv, customFieldSrv, organisationSrv)
  override def modelList: Seq[Model]               = vertexSrvList.map(_.model) :+ auditModel :+ auditedModel
  override def initialValues: Seq[InitialValue[_]] = vertexSrvList.flatMap[InitialValue[_], Seq[InitialValue[_]]](_.getInitialValues)
}
