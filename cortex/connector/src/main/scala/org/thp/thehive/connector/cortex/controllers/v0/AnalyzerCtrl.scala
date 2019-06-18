package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.cortex.services.AnalyzerSrv
import org.thp.thehive.controllers.v0.QueryCtrl
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext

@Singleton
class AnalyzerCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    analyzerSrv: AnalyzerSrv,
    val queryExecutor: CortexQueryExecutor,
    implicit val executionContext: ExecutionContext
) extends QueryCtrl {

  def list: Action[AnyContent] =
    entryPoint("list analyzer")
      .asyncAuth { implicit request ⇒
        analyzerSrv
          .listAnalyzer
          .map(l ⇒ Results.Ok(Json.toJson(l)))
      }
}
