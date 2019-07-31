package org.thp.thehive.connector.cortex.controllers.v0

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.cortex.services.AnalyzerSrv
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext

@Singleton
class AnalyzerCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    analyzerSrv: AnalyzerSrv,
    implicit val system: ActorSystem,
    implicit val ec: ExecutionContext
) {

  import WorkerConversion._

  def list: Action[AnyContent] =
    entryPoint("list analyzer")
      .asyncAuth { _ =>
        analyzerSrv
          .listAnalyzer
          .map { analyzers =>
            Results.Ok(Json.toJson(analyzers.map(toOutputWorker)))
          }
      }
}
