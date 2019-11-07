package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.services.PageSrv
import play.api.mvc._

@Singleton
class PageCtrl @Inject()(entryPoint: EntryPoint, pageSrv: PageSrv, db: Database) {

  def get(idOrTitle: String): Action[AnyContent] =
    entryPoint("get a page")
      .authRoTransaction(db) { implicit request => implicit graph =>
        pageSrv
          .get(idOrTitle)
          .visible
          .getOrFail()
          .map(p => Results.Ok(p))
      }
}
