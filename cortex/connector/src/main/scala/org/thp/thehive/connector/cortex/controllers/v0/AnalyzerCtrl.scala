package org.thp.thehive.connector.cortex.controllers.v0

import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.cortex.services.AnalyzerSrv

@Singleton
class AnalyzerCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    analyzerSrv: AnalyzerSrv,
    implicit val system: ActorSystem
) {

  import WorkerConversion._

  def list: Action[AnyContent] =
    entryPoint("list analyzer")
      .extract("range", FieldsParser.string.optional.on("range"))
      .asyncAuth { implicit request =>
        val range: Option[String] = request.body("range")
        analyzerSrv
          .listAnalyzer(range)
          .map { analyzers =>
            Results.Ok(Json.toJson(analyzers.map(toOutputWorker)))
          }
      }

  def listByType(dataType: String): Action[AnyContent] =
    entryPoint("list analyzer by dataType")
      .asyncAuth { implicit req =>
        analyzerSrv
          .listAnalyzerByType(dataType)
          .map { analyzers =>
            Results.Ok(Json.toJson(analyzers.map(toOutputWorker)))
          }
      }

  def getById(id: String): Action[AnyContent] =
    entryPoint("get analyzer by id")
      .asyncAuth { implicit req =>
        analyzerSrv
          .getAnalyzer(id)
          .map(a => Results.Ok(Json.toJson(toOutputWorker(a))))
      }
}
