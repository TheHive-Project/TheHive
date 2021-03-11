package org.thp.thehive.controllers.v1

import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputDashboard
import org.thp.thehive.models.{Dashboard, RichDashboard}
import org.thp.thehive.services.DashboardOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services.{DashboardSrv, OrganisationSrv, UserSrv}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import scala.util.Success

@Singleton
class DashboardCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    db: Database,
    dashboardSrv: DashboardSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv
) extends QueryableCtrl {

  override val entityName: String                 = "dashboard"
  override val publicProperties: PublicProperties = properties.dashboard
  override val initialQuery: Query =
    Query.init[Traversal.V[Dashboard]](
      "listDashboard",
      (graph, authContext) =>
        graph
          .union(
            organisationSrv.filterTraversal(_).get(authContext.organisation).dashboards,
            userSrv.filterTraversal(_).getByName(authContext.userId).dashboards
          )
          .dedup
    )

  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Dashboard]](
    "getDashboard",
    (idOrName, graph, authContext) => dashboardSrv.get(idOrName)(graph).visible(authContext)
  )

  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Dashboard], IteratorOutput](
    "page",
    (range, dashboardSteps, _) => dashboardSteps.richPage(range.from, range.to, withTotal = true)(_.richDashboard)
  )
  override val outputQuery: Query = Query.output[RichDashboard, Traversal.V[Dashboard]](_.richDashboard)

  def create: Action[AnyContent] =
    entrypoint("create dashboard")
      .extract("dashboard", FieldsParser[InputDashboard])
      .authTransaction(db) { implicit request => implicit graph =>
        val dashboard: InputDashboard = request.body("dashboard")
        dashboardSrv
          .create(dashboard.toDashboard)
          .flatMap {
            case richDashboard if dashboard.status == "Shared" =>
              dashboardSrv
                .share(richDashboard.dashboard, request.organisation, writable = false)
                .flatMap(_ => dashboardSrv.get(richDashboard.dashboard).richDashboard.getOrFail("Dashboard"))
            case richDashboard => Success(richDashboard)
          }
          .map(richDashboard => Results.Created(richDashboard.toJson))
      }

  def get(dashboardId: String): Action[AnyContent] =
    entrypoint("get dashboard")
      .authRoTransaction(db) { implicit request => implicit graph =>
        dashboardSrv
          .get(EntityIdOrName(dashboardId))
          .visible
          .richDashboard
          .getOrFail("Dashboard")
          .map(dashboard => Results.Ok(dashboard.toJson))
      }

  def update(dashboardId: String): Action[AnyContent] =
    entrypoint("update dashboard")
      .extract("dashboard", FieldsParser.update("dashboard", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("dashboard")
        dashboardSrv
          .update(_.get(EntityIdOrName(dashboardId)).canUpdate, propertyUpdaters) // TODO check permission
          .flatMap { case (dashboardSteps, _) => dashboardSteps.richDashboard.getOrFail("Dashboard") }
          .map(dashboard => Results.Ok(dashboard.toJson))
      }

  def delete(dashboardId: String): Action[AnyContent] =
    entrypoint("delete dashboard")
      .authTransaction(db) { implicit request => implicit graph =>
        userSrv
          .current
          .dashboards
          .get(EntityIdOrName(dashboardId))
          .getOrFail("Dashboard")
          .map { dashboard =>
            dashboardSrv.remove(dashboard)
            Results.NoContent
          }
      }
}
