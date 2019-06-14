package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.controllers.v0.QueryCtrl
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class AnalyzerCtrl @Inject()(entryPoint: EntryPoint, db: Database, val queryExecutor: CortexQueryExecutor) extends QueryCtrl {

  def list: Action[AnyContent] =
    entryPoint("list analyzer")
      .extract('query, searchParser("listAnalyzer"))
      .authTransaction(db) { implicit request ⇒ graph ⇒
        Success(Results.Ok(JsArray.empty))
      }
}
