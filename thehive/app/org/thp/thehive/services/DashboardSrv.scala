package org.thp.thehive.services

import gremlin.scala.{Graph, GremlinScala, Key, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.thehive.models.{Dashboard, Organisation, OrganisationDashboard}
import play.api.libs.json.{JsObject, Json}

import scala.util.Try

@Singleton
class DashboardSrv @Inject()(organisationSrv: OrganisationSrv, auditSrv: AuditSrv)(implicit db: Database)
    extends VertexSrv[Dashboard, DashboardSteps] {
  val organisationDashboardSrv = new EdgeSrv[OrganisationDashboard, Organisation, Dashboard]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): DashboardSteps = new DashboardSteps(raw)

  def create(
      dashboard: Dashboard,
      organisation: Organisation with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Dashboard with Entity] =
    for {
      createdDashboard <- super.create(dashboard)
      _                <- organisationDashboardSrv.create(OrganisationDashboard(), organisation, createdDashboard)
      _                <- auditSrv.dashboard.create(createdDashboard)
    } yield createdDashboard

  override def update(
      steps: DashboardSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(DashboardSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (dashboardSteps, updatedFields) =>
        dashboardSteps
          .clone()
          .getOrFail()
          .flatMap(auditSrv.dashboard.update(_, updatedFields))
    }

  def shareUpdate(dashboard: Dashboard with Entity, status: Boolean)(implicit graph: Graph, authContext: AuthContext): Try[Dashboard with Entity] =
    for {
      d <- get(dashboard).update("shared" -> status)
      _ <- auditSrv.dashboard.update(d, Json.obj("shared" -> status))
    } yield d

  def remove(dashboard: Dashboard with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      _ <- Try(get(dashboard).remove())
      _ <- auditSrv.dashboard.delete(dashboard)
    } yield ()
}

class DashboardSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Dashboard, DashboardSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): DashboardSteps = new DashboardSteps(raw)

  def visible(implicit authContext: AuthContext): DashboardSteps = filter(_.inTo[OrganisationDashboard].has(Key("name") of authContext.organisation))

  def organisation: OrganisationSteps = new OrganisationSteps(raw.inTo[OrganisationDashboard])
}
