package org.thp.thehive.connector.cortex.controllers.v0

import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.cortex.services.AnalyzerSrv
import org.thp.thehive.controllers.v0.QueryCtrl

@Singleton
class AnalyzerCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    analyzerSrv: AnalyzerSrv,
    val queryExecutor: CortexQueryExecutor,
    implicit val system: ActorSystem
) extends QueryCtrl
    with AnalyzerConversion {

  def list: Action[AnyContent] =
    entryPoint("list analyzer")
      .asyncAuth { implicit request ⇒
        analyzerSrv
          .listAnalyzer
          .map { analyzers ⇒
            Results.Ok(Json.toJson(analyzers.map(toOutputAnalyzer)))
          }
      }
}
