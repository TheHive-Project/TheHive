package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.scalactic.Accumulation._
import org.thp.scalligraph.AttributeCheckingError
import org.thp.scalligraph.controllers.{Entrypoint, Field, FieldsParser}
import org.thp.scalligraph.models.Database
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class StatsCtrl @Inject() (
    entrypoint: Entrypoint,
    queryExecutor: TheHiveQueryExecutor,
    db: Database
) {
  lazy val logger: Logger = Logger(getClass)

  def stats: Action[AnyContent] =
    entrypoint("stats")
      .extract("stats", FieldsParser[Field].sequence.on("stats"))
      .authRoTransaction(db) { implicit request => implicit graph =>
        val stats: Seq[Field] = request.body("stats")
        stats
          .validatedBy { s =>
            for {
              model <- FieldsParser.string(s.get("model"))
              queryCtrl = model match {
                case "case"         => queryExecutor.`case`
                case "task"         => queryExecutor.task
                case "log"          => queryExecutor.log
                case "alert"        => queryExecutor.alert
                case "user"         => queryExecutor.user
                case "caseTemplate" => queryExecutor.caseTemplate
                case "observable"   => queryExecutor.observable
                case "dashboard"    => queryExecutor.dashboard
                case "organisation" => queryExecutor.organisation
                case "audit"        => queryExecutor.audit
                case "profile"      => queryExecutor.profile
                case "tag"          => queryExecutor.tag
                case "page"         => queryExecutor.page
              }
              queries <- queryCtrl.statsParser(s)
            } yield queries
          }
          .badMap(errors => AttributeCheckingError(errors.toSeq))
          .toTry
          .flatMap { queries =>
            queries
              .flatten
              .toTry(query => queryExecutor.execute(query, graph, request.authContext))
              .map { outputs =>
                val results = outputs
                  .map(_.toJson)
                  .foldLeft(JsObject.empty) {
                    case (acc, o: JsObject) => acc ++ o
                    case (acc, r) =>
                      logger.warn(s"Invalid stats result: $r")
                      acc
                  }
                Results.Ok(results)
              }
          }
      }
}
