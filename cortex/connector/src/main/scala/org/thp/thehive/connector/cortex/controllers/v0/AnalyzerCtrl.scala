package org.thp.thehive.connector.cortex.controllers.v0

import akka.actor.ActorSystem
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.services.AnalyzerSrv
import org.thp.thehive.controllers.v0.Conversion._
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AnalyzerCtrl @Inject() (
    entrypoint: Entrypoint,
    analyzerSrv: AnalyzerSrv,
    implicit val system: ActorSystem,
    implicit val ec: ExecutionContext
) {

  def list: Action[AnyContent] =
    entrypoint("list analyzer")
      .extract("range", FieldsParser.string.optional.on("range"))
      .asyncAuth { implicit request =>
        val range: Option[String] = request.body("range")
        analyzerSrv
          .listAnalyzer(range)
          .map(analyzers => Results.Ok(analyzers.toSeq.toJson))
      }

  def listByType(dataType: String): Action[AnyContent] =
    entrypoint("list analyzer by dataType")
      .asyncAuth { implicit req =>
        analyzerSrv
          .listAnalyzerByType(dataType)
          .map(analyzers => Results.Ok(analyzers.toSeq.toJson))
      }

  def getById(id: String): Action[AnyContent] =
    entrypoint("get analyzer by id")
      .asyncAuth { implicit req =>
        analyzerSrv
          .getAnalyzer(id)
          .map(a => Results.Ok(a.toJson))
      }
}
