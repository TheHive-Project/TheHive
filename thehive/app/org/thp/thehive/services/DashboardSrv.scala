package org.thp.thehive.services

import scala.util.Try

import play.api.libs.json.{JsObject, Json}

import gremlin.scala.{Graph, GremlinScala, Key, P, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.{Dashboard, Organisation, OrganisationDashboard}

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
      createdDashboard <- createEntity(dashboard)
      _                <- organisationDashboardSrv.create(OrganisationDashboard(), organisation, createdDashboard)
      _                <- auditSrv.dashboard.create(createdDashboard, createdDashboard.toJson)
    } yield createdDashboard

  override def update(
      steps: DashboardSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(DashboardSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (dashboardSteps, updatedFields) =>
        dashboardSteps
          .newInstance()
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

@EntitySteps[Dashboard]
class DashboardSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Dashboard](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex] = raw): DashboardSteps = new DashboardSteps(newRaw)

  def visible(implicit authContext: AuthContext): DashboardSteps =
    this.filter(_.inTo[OrganisationDashboard].has(Key("name"), P.eq(authContext.organisation)))

  def organisation: OrganisationSteps = new OrganisationSteps(raw.inTo[OrganisationDashboard])
}
