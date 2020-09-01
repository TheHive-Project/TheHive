package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputLog
import org.thp.thehive.models.{Log, Permissions, RichLog}
import org.thp.thehive.services.LogOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.{LogSrv, OrganisationSrv, TaskSrv}
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class LogCtrl @Inject() (
    entrypoint: Entrypoint,
    @Named("with-thehive-schema") db: Database,
    properties: Properties,
    logSrv: LogSrv,
    taskSrv: TaskSrv,
    organisationSrv: OrganisationSrv
) extends QueryableCtrl
    with LogRenderer {
  lazy val logger: Logger                                   = Logger(getClass)
  override val entityName: String                           = "log"
  override val publicProperties: List[PublicProperty[_, _]] = properties.log
  override val initialQuery: Query =
    Query.init[Traversal.V[Log]]("listLog", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.tasks.logs)
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, Traversal.V[Log]](
    "getLog",
    FieldsParser[IdOrName],
    (param, graph, authContext) => logSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Log], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, logSteps, authContext) =>
      logSteps.richPage(range.from, range.to, range.extraData.contains("total"))(
        _.richLogWithCustomRenderer(logStatsRenderer(range.extraData - "total"))
      )
  )
  override val outputQuery: Query = Query.output[RichLog, Traversal.V[Log]](_.richLog)

  def create(taskId: String): Action[AnyContent] =
    entrypoint("create log")
      .extract("log", FieldsParser[InputLog])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputLog: InputLog = request.body("log")
        for {
          task <- taskSrv
            .getByIds(taskId)
            .can(Permissions.manageTask)
            .getOrFail("Task")
          createdLog <- logSrv.create(inputLog.toLog, task)
          attachment <- inputLog.attachment.map(logSrv.addAttachment(createdLog, _)).flip
          richLog = RichLog(createdLog, attachment.toList)
        } yield Results.Created(richLog.toJson)
      }

  def update(logId: String): Action[AnyContent] =
    entrypoint("update log")
      .extract("log", FieldsParser.update("log", properties.log))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("log")
        logSrv
          .update(
            _.getByIds(logId)
              .can(Permissions.manageTask),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

  def delete(logId: String): Action[AnyContent] =
    entrypoint("delete log")
      .authTransaction(db) { implicit req => implicit graph =>
        for {
          log <- logSrv.get(logId).can(Permissions.manageTask).getOrFail("Log")
          _   <- logSrv.cascadeRemove(log)
        } yield Results.NoContent
      }
}
