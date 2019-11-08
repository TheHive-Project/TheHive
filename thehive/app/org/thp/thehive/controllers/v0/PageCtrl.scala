package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputPage
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.PageSrv
import play.api.mvc._

@Singleton
class PageCtrl @Inject()(entryPoint: EntryPoint, pageSrv: PageSrv, db: Database, properties: Properties) {

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
