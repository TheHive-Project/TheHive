package org.thp.thehive.services

import java.util.{List => JList}

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

import play.api.libs.json.{JsObject, Json}

import gremlin.scala.{__, By, Element, Graph, GremlinScala, Vertex}
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Path
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.scalligraph.{EntitySteps, InternalError}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._

@Singleton
class DashboardSrv @Inject() (organisationSrv: OrganisationSrv, userSrv: UserSrv, auditSrv: AuditSrv)(implicit db: Database)
    extends VertexSrv[Dashboard, DashboardSteps] {
  val organisationDashboardSrv = new EdgeSrv[OrganisationDashboard, Organisation, Dashboard]
  val dashboardUserSrv         = new EdgeSrv[DashboardUser, Dashboard, User]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): DashboardSteps = new DashboardSteps(raw)

  def create(dashboard: Dashboard)(implicit graph: Graph, authContext: AuthContext): Try[RichDashboard] =
    for {
      createdDashboard <- createEntity(dashboard)
      user             <- userSrv.current.getOrFail()
      _                <- dashboardUserSrv.create(DashboardUser(), createdDashboard, user)
      _                <- auditSrv.dashboard.create(createdDashboard, RichDashboard(createdDashboard, Map.empty).toJson)
    } yield RichDashboard(createdDashboard, Map.empty)

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

  def share(dashboard: Dashboard with Entity, organisationName: String, writable: Boolean)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    organisationDashboardSrv
      .steps(get(dashboard).inToE[OrganisationDashboard].filter(_.outV().has("name", organisationName)).raw)
      .update("writable" -> writable)
      .flatMap {
        case d if d.isEmpty =>
          organisationSrv
            .getOrFail(organisationName)
            .flatMap(organisation => organisationDashboardSrv.create(OrganisationDashboard(writable), organisation, dashboard))
        case _ => Success(())
      }
      .flatMap { _ =>
        auditSrv.dashboard.update(dashboard, Json.obj("share" -> Json.obj("organisation" -> organisationName, "writable" -> writable)))
      }

  def unshare(dashboard: Dashboard with Entity, organisationName: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(dashboard).inToE[OrganisationDashboard].filter(_.outV().has("name", organisationName)).remove()
    Success(()) // TODO add audit
  }

  def remove(dashboard: Dashboard with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(dashboard).remove()
    auditSrv.dashboard.delete(dashboard)
  }
}

@EntitySteps[Dashboard]
class DashboardSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Dashboard](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex] = raw): DashboardSteps = new DashboardSteps(newRaw)

  def visible(implicit authContext: AuthContext): DashboardSteps =
    this.filter(_.or(_.user.current(authContext), _.inTo[OrganisationDashboard].has("name", authContext.organisation)))

  def organisation: OrganisationSteps = new OrganisationSteps(raw.inTo[OrganisationDashboard])

  def user: UserSteps = new UserSteps(raw.outTo[DashboardUser])

  def canUpdate(implicit authContext: AuthContext): DashboardSteps =
    this.filter(_.or(_.user.current(authContext), _.inToE[OrganisationDashboard].has("writable", true).outV.has("name", authContext.organisation)))

  def richDashboard: Traversal[RichDashboard, RichDashboard] =
    this
      .project(
        _.apply(By[Vertex]())
          .and(By(__[Vertex].inToE[OrganisationDashboard].outV().path.fold))
      )
      .map {
        case (dashboard, dashboardOrganisations) =>
          val organisationShares = (dashboardOrganisations: JList[Path])
            .asScala
            .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
            .map {
              case List(od, o) =>
                o.as[Organisation].name -> od.as[OrganisationDashboard].writable //.RichCustomField(cf.as[CustomField], ccf.as[CaseCustomField])
              case _ => throw InternalError("Not possible")
            }
            .toMap
          RichDashboard(dashboard.as[Dashboard], organisationShares)
      }

}
