package org.thp.thehive.services

import gremlin.scala.{Graph, GremlinScala, Key, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services._
import org.thp.thehive.models.{Dashboard, Organisation, OrganisationDashboard}

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
}

class DashboardSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Dashboard, DashboardSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): DashboardSteps = new DashboardSteps(raw)

  def visible(implicit authContext: AuthContext): DashboardSteps = filter(_.inTo[OrganisationDashboard].has(Key("name") of authContext.organisation))

  def share(implicit authContext: AuthContext): Try[Dashboard with Entity] = update("shared" -> true) // TODO add audit

  def unshare(implicit authContext: AuthContext): Try[Dashboard with Entity] = update("shared" -> false) // TODO add audit

  def remove(implicit authContext: AuthContext): Unit = { // TODO add audit
    raw.drop().iterate()
    ()
  }
}
