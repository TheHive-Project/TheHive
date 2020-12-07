package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperties, Query}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputTask
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.{CaseSrv, OrganisationSrv, ShareSrv, TaskSrv}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Success, Try}

@Singleton
class TaskCtrl @Inject() (
    entrypoint: Entrypoint,
    @Named("with-thehive-schema") db: Database,
    properties: Properties,
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    organisationSrv: OrganisationSrv,
    shareSrv: ShareSrv
) extends QueryableCtrl
    with TaskRenderer {

  override val entityName: String                 = "task"
  override val publicProperties: PublicProperties = properties.task
  override val initialQuery: Query =
    Query.init[Traversal.V[Task]]("listTask", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.tasks)
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Task], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, taskSteps, authContext) =>
      taskSteps.richPage(range.from, range.to, range.extraData.contains("total"))(
        _.richTaskWithCustomRenderer(taskStatsRenderer(range.extraData)(authContext))(authContext)
      )
  )
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Task]](
    "getTask",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, authContext) => taskSrv.get(idOrName)(graph).visible(authContext)
  )
  override val outputQuery: Query =
    Query.outputWithContext[RichTask, Traversal.V[Task]]((taskSteps, authContext) => taskSteps.richTask(authContext))
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query.init[Traversal.V[Task]](
      "waitingTask",
      (graph, authContext) => taskSrv.startTraversal(graph).has(_.status, TaskStatus.Waiting).visible(authContext)
    ),
    Query[Traversal.V[Task], Traversal.V[User]]("assignableUsers", (taskSteps, authContext) => taskSteps.assignableUsers(authContext)),
    Query[Traversal.V[Task], Traversal.V[Log]]("logs", (taskSteps, _) => taskSteps.logs),
    Query[Traversal.V[Task], Traversal.V[Case]]("case", (taskSteps, _) => taskSteps.`case`),
    Query[Traversal.V[Task], Traversal.V[Organisation]]("organisations", (taskSteps, authContext) => taskSteps.organisations.visible(authContext))
  )

  def create: Action[AnyContent] =
    entrypoint("create task")
      .extract("task", FieldsParser[InputTask])
      .extract("caseId", FieldsParser[String])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputTask: InputTask = request.body("task")
        val caseId: String       = request.body("caseId")
        for {
          case0        <- caseSrv.get(EntityIdOrName(caseId)).can(Permissions.manageTask).getOrFail("Case")
          createdTask  <- taskSrv.create(inputTask.toTask, None)
          organisation <- organisationSrv.getOrFail(request.organisation)
          _            <- shareSrv.shareTask(createdTask, case0, organisation)
        } yield Results.Created(createdTask.toJson)
      }

  def get(taskId: String): Action[AnyContent] =
    entrypoint("get task")
      .authRoTransaction(db) { implicit request => implicit graph =>
        taskSrv
          .get(EntityIdOrName(taskId))
          .visible
          .richTask
          .getOrFail("Task")
          .map(task => Results.Ok(task.toJson))
      }

  def list: Action[AnyContent] =
    entrypoint("list task")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val tasks = taskSrv
          .startTraversal
          .visible
          .richTask
          .toSeq
        Success(Results.Ok(tasks.toJson))
      }

  def update(taskId: String): Action[AnyContent] =
    entrypoint("update task")
      .extract("task", FieldsParser.update("task", properties.task))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("task")
        taskSrv
          .update(
            _.get(EntityIdOrName(taskId))
              .can(Permissions.manageTask),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

  def isActionRequired(taskId: String): Action[AnyContent] =
    entrypoint("is action required")
      .authTransaction(db){ implicit request => implicit graph =>
        val taskTraversal = taskSrv.get(EntityIdOrName(taskId))
        for {
          task  <- taskTraversal.clone().visible.getOrFail("Task")
          orgas <- Try(taskTraversal.in[ShareTask].in[OrganisationShare].v[Organisation].visible.toSeq)
        } yield Results.Ok(taskSrv.isActionRequired(task, orgas).toJson)
      }


  def actionRequired(taskId: String, orgaId: String, required: Boolean): Action[AnyContent] =
    entrypoint("action required")
      .authTransaction(db){ implicit request => implicit graph =>
        for {
          organisation <- organisationSrv.get(EntityIdOrName(orgaId)).getOrFail("Organisation")
          task         <- taskSrv.get(EntityIdOrName(taskId)).getOrFail("Task")
          _            <- taskSrv.actionRequired(task, organisation, required)
        } yield Results.NoContent
      }

}
