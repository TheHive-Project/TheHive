package org.thp.thehive.controllers.v0

import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputDashboard
import org.thp.thehive.models.Dashboard
import org.thp.thehive.services.{DashboardSrv, DashboardSteps, OrganisationSrv}

@Singleton
class DashboardCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    properties: Properties,
    dashboardSrv: DashboardSrv,
    organisationSrv: OrganisationSrv
) extends QueryableCtrl {
  val entityName: String                           = "dashboard"
  val publicProperties: List[PublicProperty[_, _]] = properties.dashboard ::: metaProperties[DashboardSteps]

  val initialQuery: Query =
    Query.init[DashboardSteps]("listDashboard", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).dashboards)

  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, DashboardSteps](
    "getDashboard",
    FieldsParser[IdOrName],
    (param, graph, authContext) => dashboardSrv.get(param.idOrName)(graph).visible(authContext)
  )

  val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, DashboardSteps, PagedResult[Dashboard with Entity]](
    "page",
    FieldsParser[OutputParam],
    (range, dashboardSteps, _) => dashboardSteps.page(range.from, range.to, withTotal = true)
  )
  val outputQuery: Query = Query.output[Dashboard with Entity]()

  def create: Action[AnyContent] =
    entryPoint("create dashboard")
      .extract("dashboard", FieldsParser[InputDashboard])
      .authTransaction(db) { implicit request => implicit graph =>
        val dashboard: InputDashboard = request.body("dashboard")
        for {
          organisation     <- organisationSrv.current.getOrFail()
          createdDashboard <- dashboardSrv.create(dashboard.toDashboard, organisation)
        } yield Results.Created(createdDashboard.toJson)
      }

  def get(dashboardId: String): Action[AnyContent] =
    entryPoint("get dashboard")
      .authRoTransaction(db) { implicit request => implicit graph =>
        dashboardSrv
          .getByIds(dashboardId)
          .visible
          .getOrFail()
          .map { dashboard =>
            Results.Ok(dashboard.toJson)
          }
      }

  def update(dashboardId: String): Action[AnyContent] =
    entryPoint("update dashboard")
      .extract("dashboard", FieldsParser.update("dashboard", properties.dashboard))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("dashboard")
        dashboardSrv
          .update(_.getByIds(dashboardId) /*.can(Permissions.manageAlert)*/, propertyUpdaters) // TODO check permission
          .flatMap { case (dashboardSteps, _) => dashboardSteps.getOrFail() }
          .map(dashboard => Results.Ok(dashboard.toJson))
      }

  def delete(dashboardId: String): Action[AnyContent] =
    entryPoint("delete dashboard")
      .authTransaction(db) { implicit request => implicit graph =>
        dashboardSrv
          .getOrFail(dashboardId)
          .map { dashboard =>
            dashboardSrv.remove(dashboard)
            Results.NoContent
          }
      }
}
