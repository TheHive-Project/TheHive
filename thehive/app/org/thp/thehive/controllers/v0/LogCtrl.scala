package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputLog
import org.thp.thehive.models.{Log, Permissions, RichLog}
import org.thp.thehive.services.LogOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.{LogSrv, OrganisationSrv, TaskSrv}
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class LogCtrl @Inject() (
    override val entrypoint: Entrypoint,
    @Named("with-thehive-schema") override val db: Database,
    logSrv: LogSrv,
    taskSrv: TaskSrv,
    @Named("v0") override val queryExecutor: QueryExecutor,
    override val publicData: PublicLog
) extends QueryCtrl {

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
          createdLog <- logSrv.create(inputLog.toLog, task)
          attachment <- inputLog.attachment.map(logSrv.addAttachment(createdLog, _)).flip
          richLog = RichLog(createdLog, attachment.toList)
        } yield Results.Created(richLog.toJson)
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
          .map(_ => Results.NoContent)
      }

  def delete(logId: String): Action[AnyContent] =
    entrypoint("delete log")
      .authTransaction(db) { implicit req => implicit graph =>
        for {
          log <- logSrv.get(EntityIdOrName(logId)).can(Permissions.manageTask).getOrFail("Log")
          _   <- logSrv.cascadeRemove(log)
        } yield Results.NoContent
      }
}

@Singleton
class PublicLog @Inject() (logSrv: LogSrv, organisationSrv: OrganisationSrv) extends PublicData {
  override val entityName: String = "log"
  override val initialQuery: Query =
    Query.init[Traversal.V[Log]]("listLog", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.tasks.logs)
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Log]](
    "getLog",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, authContext) => logSrv.get(idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Log], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, logSteps, _) => logSteps.richPage(range.from, range.to, withTotal = true)(_.richLog)
  )
  override val outputQuery: Query = Query.output[RichLog, Traversal.V[Log]](_.richLog)
  override val publicProperties: PublicProperties =
    PublicPropertyListBuilder[Log]
      .property("message", UMapping.string)(_.field.updatable)
      .property("deleted", UMapping.boolean)(_.field.updatable)
      .property("startDate", UMapping.date)(_.rename("date").readonly)
      .property("status", UMapping.string)(_.select(_.constant("Ok")).readonly)
      .property("attachment", UMapping.string)(_.select(_.attachments.value(_.attachmentId)).readonly)
      .build
}
