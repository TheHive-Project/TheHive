package org.thp.thehive.controllers.v0

import org.thp.scalligraph.controllers.{Entrypoint, FString, FieldsParser}
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.scalligraph.{EntityIdOrName, InvalidFormatAttributeError}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputDashboard
import org.thp.thehive.models.{Dashboard, RichDashboard}
import org.thp.thehive.services.DashboardOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services.{DashboardSrv, OrganisationSrv, UserSrv}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Failure, Success}

class DashboardCtrl(
    override val entrypoint: Entrypoint,
    dashboardSrv: DashboardSrv,
    userSrv: UserSrv,
    implicit val db: Database,
    override val publicData: PublicDashboard,
    override val queryExecutor: QueryExecutor
) extends QueryCtrl {
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
      .extract("dashboard", FieldsParser.update("dashboard", publicData.publicProperties))
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
        dashboardSrv
          .get(EntityIdOrName(dashboardId))
          .canUpdate
          .getOrFail("Dashboard")
          .map { dashboard =>
            dashboardSrv.remove(dashboard)
            Results.NoContent
          }
      }
}

class PublicDashboard(
    dashboardSrv: DashboardSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv
) extends PublicData {
  val entityName: String = "dashboard"

  val initialQuery: Query =
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

  val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Dashboard], IteratorOutput](
    "page",
    (range, dashboardSteps, authContext) => dashboardSteps.richPage(range.from, range.to, withTotal = true)(_.richDashboard(authContext))
  )
  override val outputQuery: Query = Query.outputWithContext[RichDashboard, Traversal.V[Dashboard]](_.richDashboard(_))
  val publicProperties: PublicProperties = PublicPropertyListBuilder[Dashboard]
    .property("title", UMapping.string)(_.field.updatable)
    .property("description", UMapping.string)(_.field.updatable)
    .property("definition", UMapping.string)(_.field.updatable)
    .property("status", UMapping.string)(
      _.select(_.choose(_.organisation, "Shared", "Private"))
        .custom {
          case (_, "Shared", vertex, graph, authContext) =>
            for {
              dashboard <- dashboardSrv.get(vertex)(graph).filter(_.user.current(authContext)).getOrFail("Dashboard")
              _         <- dashboardSrv.share(dashboard, authContext.organisation, writable = true)(graph, authContext)
            } yield Json.obj("status" -> "Shared")

          case (_, "Private", vertex, graph, authContext) =>
            for {
              d <- dashboardSrv.get(vertex)(graph).filter(_.user.current(authContext)).getOrFail("Dashboard")
              _ <- dashboardSrv.unshare(d, authContext.organisation)(graph, authContext)
            } yield Json.obj("status" -> "Private")

          case (_, "Deleted", vertex, graph, authContext) =>
            for {
              d <- dashboardSrv.get(vertex)(graph).filter(_.user.current(authContext)).getOrFail("Dashboard")
              _ <- dashboardSrv.remove(d)(graph, authContext)
            } yield Json.obj("status" -> "Deleted")

          case (_, status, _, _, _) =>
            Failure(InvalidFormatAttributeError("status", "String", Set("Shared", "Private", "Deleted"), FString(status)))
        }
    )
    .build
}
