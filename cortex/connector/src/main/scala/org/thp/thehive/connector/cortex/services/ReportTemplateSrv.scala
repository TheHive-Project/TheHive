package org.thp.thehive.connector.cortex.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.models.{BaseVertexSteps, Database}
import org.thp.scalligraph.services._
import org.thp.thehive.connector.cortex.models.ReportTemplate
import org.thp.thehive.connector.cortex.models.ReportType.ReportType

@Singleton
class ReportTemplateSrv @Inject()(
    implicit db: Database
) extends VertexSrv[ReportTemplate, ReportTemplateSteps] {

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ReportTemplateSteps = new ReportTemplateSteps(raw)
}

@EntitySteps[ReportTemplate]
class ReportTemplateSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph)
    extends BaseVertexSteps[ReportTemplate, ReportTemplateSteps](raw) {

  def forWorkerAndType(workerId: String, rType: ReportType): ReportTemplateSteps = newInstance(
    raw.and(
      _.has(Key("workerId") of workerId),
      _.has(Key("reportType") of rType)
    )
  )

  override def newInstance(raw: GremlinScala[Vertex]): ReportTemplateSteps = new ReportTemplateSteps(raw)
}
