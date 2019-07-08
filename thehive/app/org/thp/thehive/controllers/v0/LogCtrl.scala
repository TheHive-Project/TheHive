package org.thp.thehive.controllers.v0

import scala.util.Try

import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.thehive.dto.v0.{InputLog, OutputLog}
import org.thp.thehive.models.{Permissions, RichLog}
import org.thp.thehive.services.{LogSrv, LogSteps, OrganisationSrv, TaskSrv}

@Singleton
class LogCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    logSrv: LogSrv,
    taskSrv: TaskSrv,
    organisationSrv: OrganisationSrv
) extends QueryableCtrl {
  import LogConversion._

  lazy val logger                                           = Logger(getClass)
  override val entityName: String                           = "log"
  override val publicProperties: List[PublicProperty[_, _]] = logProperties
  override val initialQuery: ParamQuery[_] =
    Query.init[LogSteps]("listLog", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.tasks.logs)
  override val pageQuery: ParamQuery[_] = Query.withParam[OutputParam, LogSteps, PagedResult[RichLog]](
    "page",
    FieldsParser[OutputParam],
    (range, logSteps, _) => logSteps.richPage(range.from, range.to, range.withSize.getOrElse(false))(_.richLog.raw)
  )
  override val outputQuery: ParamQuery[_] = Query.output[RichLog, OutputLog]

  def create(taskId: String): Action[AnyContent] =
    entryPoint("create log")
      .extract("log", FieldsParser[InputLog])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputLog: InputLog = request.body("log")
        for {
          task <- taskSrv
            .get(taskId)
            .can(Permissions.manageTask)
            .getOrFail()
          createdLog <- logSrv.create(inputLog, task)
          attachment <- inputLog.attachment.map(logSrv.addAttachment(createdLog, _)).flip
          richLog = RichLog(createdLog, attachment.toList)
        } yield Results.Created(richLog.toJson)
      }

  def update(logId: String): Action[AnyContent] =
    entryPoint("update log")
      .extract("log", FieldsParser.update("log", logProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("log")
        logSrv
          .update(
            _.get(logId)
              .can(Permissions.manageTask),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

  def delete(logId: String): Action[AnyContent] =
    entryPoint("update log")
      .authTransaction(db) { implicit request => implicit graph =>
        Try(logSrv.initSteps.remove(logId)) // FIXME use service instead of step in order to generate audit log
          .map(_ => Results.NoContent)
      }
}
