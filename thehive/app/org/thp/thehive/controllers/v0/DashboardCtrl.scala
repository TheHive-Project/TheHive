package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
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

@Singleton
class DashboardCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    dashboardSrv: DashboardSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    @Named("with-thehive-schema") implicit val db: Database
) extends QueryableCtrl {
  val entityName: String                           = "dashboard"
  val publicProperties: List[PublicProperty[_, _]] = properties.dashboard

  val initialQuery: Query =
    Query.init[Traversal.V[Dashboard]](
      "listDashboard",
      (graph, authContext) =>
        Traversal
          .union(
            organisationSrv.filterTraversal(_).get(authContext.organisation).dashboards,
            userSrv.filterTraversal(_).get(authContext.userId).dashboards
          )(graph)
          .dedup
    )

  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, Traversal.V[Dashboard]](
    "getDashboard",
    FieldsParser[IdOrName],
    (param, graph, authContext) => dashboardSrv.get(param.idOrName)(graph).visible(authContext)
  )

  val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Dashboard], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, dashboardSteps, _) => dashboardSteps.richPage(range.from, range.to, withTotal = true)(_.richDashboard)
  )
  override val outputQuery: Query = Query.output[RichDashboard, Traversal.V[Dashboard]](_.richDashboard)

  def create: Action[AnyContent] =
    entrypoint("create dashboard")
      .extract("dashboard", FieldsParser[InputDashboard])
      .authTransaction(db) { implicit request => implicit graph =>
        val dashboard: InputDashboard = request.body("dashboard")
        dashboardSrv.create(dashboard.toDashboard).map(d => Results.Created(d.toJson))
      }

  def get(dashboardId: String): Action[AnyContent] =
    entrypoint("get dashboard")
      .authRoTransaction(db) { implicit request => implicit graph =>
        dashboardSrv
          .getByIds(dashboardId)
          .visible
          .richDashboard
          .getOrFail("Dashboard")
          .map(dashboard => Results.Ok(dashboard.toJson))
      }

  def update(dashboardId: String): Action[AnyContent] =
    entrypoint("update dashboard")
      .extract("dashboard", FieldsParser.update("dashboard", properties.dashboard))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("dashboard")
        dashboardSrv
          .update(_.getByIds(dashboardId).canUpdate, propertyUpdaters) // TODO check permission
          .flatMap { case (dashboardSteps, _) => dashboardSteps.richDashboard.getOrFail("Dashboard") }
          .map(dashboard => Results.Ok(dashboard.toJson))
      }

  def delete(dashboardId: String): Action[AnyContent] =
    entrypoint("delete dashboard")
      .authTransaction(db) { implicit request => implicit graph =>
        userSrv
          .current
          .dashboards
          .getByIds(dashboardId)
          .getOrFail("Dashboard")
          .map { dashboard =>
            dashboardSrv.remove(dashboard)
            Results.NoContent
          }
      }
}
