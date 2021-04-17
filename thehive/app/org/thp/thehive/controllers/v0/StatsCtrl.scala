package org.thp.thehive.controllers.v0

import org.scalactic.Accumulation._
import org.thp.scalligraph.AttributeCheckingError
import org.thp.scalligraph.controllers.{Entrypoint, Field, FieldsParser}
import org.thp.scalligraph.models.Database
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.mvc.{Action, AnyContent, Results}

class StatsCtrl(
    entrypoint: Entrypoint,
    caseCtrl: CaseCtrl,
    taskCtrl: TaskCtrl,
    logCtrl: LogCtrl,
    alertCtrl: AlertCtrl,
    userCtrl: UserCtrl,
    caseTemplateCtrl: CaseTemplateCtrl,
    observableCtrl: ObservableCtrl,
    dashboardCtrl: DashboardCtrl,
    organisationCtrl: OrganisationCtrl,
    auditCtrl: AuditCtrl,
    profileCtrl: ProfileCtrl,
    tagCtrl: TagCtrl,
    pageCtrl: PageCtrl,
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
              queryCtrl: QueryCtrl = model match {
                case "case"          => caseCtrl
                case "case_task"     => taskCtrl
                case "case_task_log" => logCtrl
                case "alert"         => alertCtrl
                case "user"          => userCtrl
                case "caseTemplate"  => caseTemplateCtrl
                case "case_artifact" => observableCtrl
                case "dashboard"     => dashboardCtrl
                case "organisation"  => organisationCtrl
                case "audit"         => auditCtrl
                case "profile"       => profileCtrl
                case "tag"           => tagCtrl
                case "page"          => pageCtrl
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
                    case (acc, o: JsObject) => acc deepMerge o
                    case (acc, r) =>
                      logger.warn(s"Invalid stats result: $r")
                      acc
                  }
                Results.Ok(results)
              }
          }
      }
}
