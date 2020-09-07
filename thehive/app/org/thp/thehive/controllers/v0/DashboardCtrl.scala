package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.InvalidFormatAttributeError
import org.thp.scalligraph.controllers.{Entrypoint, FString, FieldsParser}
import org.thp.scalligraph.models.{Database, UMapping}
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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Failure

@Singleton
class DashboardCtrl @Inject() (
    override val entrypoint: Entrypoint,
    dashboardSrv: DashboardSrv,
    userSrv: UserSrv,
    @Named("with-thehive-schema") implicit val db: Database,
    override val publicData: PublicDashboard,
    @Named("v0") override val queryExecutor: QueryExecutor
) extends QueryCtrl {
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
      .extract("dashboard", FieldsParser.update("dashboard", publicData.publicProperties))
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

@Singleton
class PublicDashboard @Inject() (
    dashboardSrv: DashboardSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    @Named("with-thehive-schema") db: Database
) extends PublicData {
  val entityName: String = "dashboard"

  val initialQuery: Query =
    Query.init[Traversal.V[Dashboard]](
      "listDashboard",
      (graph, authContext) =>
        Traversal
          .union(
            organisationSrv.filterTraversal(_).get(authContext.organisation)(db).dashboards,
            userSrv.filterTraversal(_).get(authContext.userId)(db).dashboards
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
  val publicProperties: PublicProperties = PublicPropertyListBuilder[Dashboard]
    .property("title", UMapping.string)(_.field.updatable)
    .property("description", UMapping.string)(_.field.updatable)
    .property("definition", UMapping.string)(_.field.updatable)
    .property("status", UMapping.string)(
      _.select(_.organisation.fold.domainMap(d => if (d.isEmpty) "Private" else "Shared")).custom { // TODO replace by choose step
        case (_, "Shared", vertex, _, graph, authContext) =>
          for {
            dashboard <- dashboardSrv.get(vertex)(graph).filter(_.user.current(authContext)).getOrFail("Dashboard")
            _         <- dashboardSrv.share(dashboard, authContext.organisation, writable = false)(graph, authContext)
          } yield Json.obj("status" -> "Shared")

        case (_, "Private", vertex, _, graph, authContext) =>
          for {
            d <- dashboardSrv.get(vertex)(graph).filter(_.user.current(authContext)).getOrFail("Dashboard")
            _ <- dashboardSrv.unshare(d, authContext.organisation)(graph, authContext)
          } yield Json.obj("status" -> "Private")

        case (_, "Deleted", vertex, _, graph, authContext) =>
          for {
            d <- dashboardSrv.get(vertex)(graph).filter(_.user.current(authContext)).getOrFail("Dashboard")
            _ <- dashboardSrv.remove(d)(graph, authContext)
          } yield Json.obj("status" -> "Deleted")

        case (_, status, _, _, _, _) =>
          Failure(InvalidFormatAttributeError("status", "String", Set("Shared", "Private", "Deleted"), FString(status)))
      }
    )
    .build
}
