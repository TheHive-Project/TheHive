package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputDashboard
import org.thp.thehive.models.RichDashboard
import org.thp.thehive.services.{DashboardSrv, DashboardSteps, OrganisationSrv, UserSrv}
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class DashboardCtrl @Inject() (
    entrypoint: Entrypoint,
    db: Database,
    properties: Properties,
    dashboardSrv: DashboardSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv
) extends QueryableCtrl {
  val entityName: String                           = "dashboard"
  val publicProperties: List[PublicProperty[_, _]] = properties.dashboard ::: metaProperties[DashboardSteps]

  val initialQuery: Query =
    Query.init[DashboardSteps](
      "listDashboard",
      (graph, authContext) =>
        union(dashboardSrv)(
          t => organisationSrv.steps(db.labelFilter(organisationSrv.model)(t))(graph).get(authContext.organisation).dashboards,
          t => userSrv.steps(db.labelFilter(userSrv.model)(t))(graph).current(authContext).dashboards
        )(graph).dedup
    )

  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, DashboardSteps](
    "getDashboard",
    FieldsParser[IdOrName],
    (param, graph, authContext) => dashboardSrv.get(param.idOrName)(graph).visible(authContext)
  )

  val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, DashboardSteps, PagedResult[RichDashboard]](
    "page",
    FieldsParser[OutputParam],
    (range, dashboardSteps, _) => dashboardSteps.richPage(range.from, range.to, withTotal = true)(_.richDashboard)
  )
  override val outputQuery: Query = Query.output[RichDashboard, DashboardSteps](_.richDashboard)

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
          .getOrFail()
          .map(dashboard => Results.Ok(dashboard.toJson))
      }

  def update(dashboardId: String): Action[AnyContent] =
    entrypoint("update dashboard")
      .extract("dashboard", FieldsParser.update("dashboard", properties.dashboard))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("dashboard")
        dashboardSrv
          .update(_.getByIds(dashboardId).canUpdate, propertyUpdaters) // TODO check permission
          .flatMap { case (dashboardSteps, _) => dashboardSteps.richDashboard.getOrFail() }
          .map(dashboard => Results.Ok(dashboard.toJson))
      }

  def delete(dashboardId: String): Action[AnyContent] =
    entrypoint("delete dashboard")
      .authTransaction(db) { implicit request => implicit graph =>
        userSrv
          .current
          .dashboards
          .getByIds(dashboardId)
          .getOrFail()
          .map { dashboard =>
            dashboardSrv.remove(dashboard)
            Results.NoContent
          }
      }
}
