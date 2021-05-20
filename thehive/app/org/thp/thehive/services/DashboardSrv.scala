package org.thp.thehive.services

import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import play.api.libs.json.{JsObject, Json}

import java.util.{List => JList, Map => JMap}
import scala.util.{Success, Try}

class DashboardSrv(organisationSrv: OrganisationSrv, userSrv: UserSrv, auditSrv: AuditSrv) extends VertexSrv[Dashboard] with TheHiveOpsNoDeps {
  val organisationDashboardSrv = new EdgeSrv[OrganisationDashboard, Organisation, Dashboard]
  val dashboardUserSrv         = new EdgeSrv[DashboardUser, Dashboard, User]

  def create(dashboard: Dashboard)(implicit graph: Graph, authContext: AuthContext): Try[RichDashboard] =
    for {
      createdDashboard <- createEntity(dashboard)
      user             <- userSrv.current.getOrFail("User")
      _                <- dashboardUserSrv.create(DashboardUser(), createdDashboard, user)
      richDashboard = RichDashboard(createdDashboard, Map.empty, writable = true)
      _ <- auditSrv.dashboard.create(createdDashboard, richDashboard.toJson)
    } yield richDashboard

  override def update(
      traversal: Traversal.V[Dashboard],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Dashboard], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (dashboardSteps, updatedFields) =>
        dashboardSteps
          .clone()
          .getOrFail("Dashboard")
          .flatMap(auditSrv.dashboard.update(_, updatedFields))
    }

  def share(dashboard: Dashboard with Entity, organisation: EntityIdOrName, writable: Boolean)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    organisationSrv.get(organisation).getOrFail("Organisation").flatMap { org =>
      get(dashboard)
        .inE[OrganisationDashboard]
        .filter(_.outV.v[Organisation].getEntity(org))
        .update(_.writable, writable)
        .fold
        .getOrFail("Dashboard")
        .flatMap {
          case d if d.isEmpty =>
            organisationSrv
              .get(organisation)
              .getOrFail("Organisation")
              .flatMap(organisation => organisationDashboardSrv.create(OrganisationDashboard(writable), organisation, dashboard))
          case _ => Success(())
        }
        .flatMap { _ =>
          auditSrv.dashboard.update(dashboard, Json.obj("share" -> Json.obj("organisation" -> org.name, "writable" -> writable)))
        }
    }

  def unshare(dashboard: Dashboard with Entity, organisation: EntityIdOrName)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(dashboard).inE[OrganisationDashboard].filter(_.outV.v[Organisation].get(organisation)).remove()
    Success(()) // TODO add audit
  }

  def remove(dashboard: Dashboard with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    organisationSrv.getOrFail(authContext.organisation).flatMap { organisation =>
      get(dashboard).remove()
      auditSrv.dashboard.delete(dashboard, organisation)
    }
}

trait DashboardOps { _: TheHiveOpsNoDeps =>

  implicit class DashboardOpsDefs(traversal: Traversal.V[Dashboard]) {

    def get(idOrName: EntityIdOrName): Traversal.V[Dashboard] =
      idOrName.fold(traversal.getByIds(_), _ => traversal.empty)

    def visible(implicit authContext: AuthContext): Traversal.V[Dashboard] =
      traversal.filter(_.or(_.user.current, _.organisation.current))

    def organisation: Traversal.V[Organisation] = traversal.in[OrganisationDashboard].v[Organisation]

    def user: Traversal.V[User] = traversal.out[DashboardUser].v[User]

    def canUpdate(implicit authContext: AuthContext): Traversal.V[Dashboard] =
      traversal.filter(
        _.or(_.user.current, _.inE[OrganisationDashboard].has(_.writable, true).outV.v[Organisation].current)
      )

    def organisationShares: Traversal[Seq[(String, Boolean)], JList[JMap[String, Any]], Converter[Seq[(String, Boolean)], JList[JMap[String, Any]]]] =
      traversal
        .inE[OrganisationDashboard]
        .project(
          _.byValue(_.writable)
            .by(_.outV)
        )
        .fold
        .domainMap(_.map { case (writable, orgs) => (orgs.value[String]("name"), writable) })

    def richDashboard(implicit authContext: AuthContext): Traversal[RichDashboard, JMap[String, Any], Converter[RichDashboard, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.organisationShares)
            .by(_.choose(_.canUpdate, true, false))
        )
        .domainMap {
          case (dashboard, organisationShares, writable) => RichDashboard(dashboard, organisationShares.toMap, writable)
        }

  }

}
