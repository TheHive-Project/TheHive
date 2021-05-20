package org.thp.thehive.controllers.v0

import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputLog
import org.thp.thehive.models.{Log, Permissions, RichLog}
import org.thp.thehive.services.{CustomFieldSrv, LogSrv, OrganisationSrv, TaskSrv, TheHiveOps, TheHiveOpsNoDeps}
import play.api.mvc.{Action, AnyContent, Results}

class LogCtrl(
    override val entrypoint: Entrypoint,
    override val db: Database,
    logSrv: LogSrv,
    taskSrv: TaskSrv,
    override val queryExecutor: QueryExecutor,
    override val publicData: PublicLog
) extends QueryCtrl
    with TheHiveOpsNoDeps {

  def create(taskId: String): Action[AnyContent] =
    entrypoint("create log")
      .extract("log", FieldsParser[InputLog])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputLog: InputLog = request.body("log")
        for {
          task <-
            taskSrv
              .get(EntityIdOrName(taskId))
              .can(Permissions.manageTask)
              .getOrFail("Task")
          createdLog <- logSrv.create(inputLog.toLog, task, inputLog.attachment)
        } yield Results.Created(createdLog.toJson)
      }

  def update(logId: String): Action[AnyContent] =
    entrypoint("update log")
      .extract("log", FieldsParser.update("log", publicData.publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("log")
        logSrv
          .update(
            _.get(EntityIdOrName(logId))
              .can(Permissions.manageTask),
            propertyUpdaters
          )
          .flatMap {
            case (logs, _) =>
              logs
                .richLog
                .getOrFail("Log")
                .map(richLog => Results.Ok(richLog.toJson))
          }
      }

  def delete(logId: String): Action[AnyContent] =
    entrypoint("delete log")
      .authTransaction(db) { implicit req => implicit graph =>
        for {
          log <- logSrv.get(EntityIdOrName(logId)).can(Permissions.manageTask).getOrFail("Log")
          _   <- logSrv.delete(log)
        } yield Results.NoContent
      }
}

class PublicLog(logSrv: LogSrv, override val organisationSrv: OrganisationSrv, override val customFieldSrv: CustomFieldSrv)
    extends PublicData
    with TheHiveOps {
  override val entityName: String = "log"
  override val initialQuery: Query =
    Query.init[Traversal.V[Log]]("listLog", (graph, authContext) => logSrv.startTraversal(graph).visible(authContext))
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Log]](
    "getLog",
    (idOrName, graph, authContext) => logSrv.get(idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Log], IteratorOutput](
    "page",
    {

      case (OutputParam(from, to, _, 0), logSteps, _) => logSteps.richPage(from, to, withTotal = true)(_.richLog)
      case (OutputParam(from, to, _, _), logSteps, authContext) =>
        logSteps.richPage(from, to, withTotal = true)(
          _.richLogWithCustomRenderer(
            _.task.richTaskWithCustomRenderer(
              _.`case`.richCase(authContext).option
            )
          )
        )
    }
  )

  override val outputQuery: Query = Query.output[RichLog, Traversal.V[Log]](_.richLog)
  override val publicProperties: PublicProperties =
    PublicPropertyListBuilder[Log]
      .property("message", UMapping.string)(_.field.updatable)
      .property("deleted", UMapping.boolean)(_.field.updatable)
      .property("startDate", UMapping.date)(_.rename("date").readonly)
      .property("status", UMapping.string)(_.select(_.constant("Ok")).readonly)
      .property("attachment.name", UMapping.string.optional)(_.select(_.attachments.value(_.name)).readonly)
      .property("attachment.hashes", UMapping.hash.sequence)(_.select(_.attachments.value(_.hashes)).readonly)
      .property("attachment.size", UMapping.long.optional)(_.select(_.attachments.value(_.size)).readonly)
      .property("attachment.contentType", UMapping.string.optional)(_.select(_.attachments.value(_.contentType)).readonly)
      .property("attachment.id", UMapping.string.optional)(_.select(_.attachments.value(_.attachmentId)).readonly)
      .build
}
