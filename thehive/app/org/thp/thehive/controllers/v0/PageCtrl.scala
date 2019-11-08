package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputPage
import org.thp.thehive.models.{Page, Permissions}
import org.thp.thehive.services.{OrganisationSrv, PageSrv, PageSteps}
import play.api.mvc._

@Singleton
class PageCtrl @Inject()(entryPoint: EntryPoint, pageSrv: PageSrv, db: Database, properties: Properties, organisationSrv: OrganisationSrv)
    extends QueryableCtrl {

  override val entityName: String                           = "page"
  override val publicProperties: List[PublicProperty[_, _]] = properties.page ::: metaProperties[PageSteps]
  override val initialQuery: Query =
    Query.init[PageSteps]("listPage", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).pages)
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, PageSteps](
    "getPage",
    FieldsParser[IdOrName],
    (param, graph, authContext) => pageSrv.get(param.idOrName)(graph).visible(authContext)
  )
  val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, PageSteps, PagedResult[Page with Entity]](
    "page",
    FieldsParser[OutputParam],
    (range, pageSteps, _) => pageSteps.page(range.from, range.to, withTotal = true)
  )
  val outputQuery: Query = Query.output[Page with Entity]()

  def get(idOrTitle: String): Action[AnyContent] =
    entryPoint("get a page")
      .authRoTransaction(db) { implicit request => implicit graph =>
        pageSrv
          .get(idOrTitle)
          .visible
          .getOrFail()
          .map(p => Results.Ok(p.toJson))
      }

  def create: Action[AnyContent] =
    entryPoint("create a page")
      .extract("page", FieldsParser[InputPage])
      .authPermittedTransaction(db, Permissions.managePage) { implicit request => implicit graph =>
        val page: InputPage = request.body("page")

        pageSrv
          .create(page.toPage)
          .map(p => Results.Created(p.toJson))
      }

  def update(idOrTitle: String): Action[AnyContent] =
    entryPoint("update a page")
      .extract("page", FieldsParser.update("page", properties.page))
      .authPermittedTransaction(db, Permissions.managePage) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("page")

        for {
          page    <- pageSrv.get(idOrTitle).visible.getOrFail()
          updated <- pageSrv.update(page, propertyUpdaters)
        } yield Results.Ok(updated.toJson)
      }

  def delete(idOrTitle: String): Action[AnyContent] =
    entryPoint("delete a page")
      .authPermittedTransaction(db, Permissions.managePage) { implicit request => implicit graph =>
        for {
          page <- pageSrv.get(idOrTitle).visible.getOrFail()
          _    <- pageSrv.delete(page)
        } yield Results.NoContent
      }
}
