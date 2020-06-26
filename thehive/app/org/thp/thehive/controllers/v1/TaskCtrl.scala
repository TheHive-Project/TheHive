package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputTask
import org.thp.thehive.models.{Permissions, RichTask}
import org.thp.thehive.services.{CaseSrv, CaseSteps, LogSteps, OrganisationSrv, OrganisationSteps, ShareSrv, TaskSrv, TaskSteps, UserSteps}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class TaskCtrl @Inject() (
    entrypoint: Entrypoint,
    @Named("with-thehive-schema") db: Database,
    properties: Properties,
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    organisationSrv: OrganisationSrv,
    shareSrv: ShareSrv
) extends QueryableCtrl {

  override val entityName: String                           = "task"
  override val publicProperties: List[PublicProperty[_, _]] = properties.task ::: metaProperties[TaskSteps]
  override val initialQuery: Query =
    Query.init[TaskSteps]("listTask", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.tasks)
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, TaskSteps, PagedResult[RichTask]](
    "page",
    FieldsParser[OutputParam],
    (range, taskSteps, _) => taskSteps.richPage(range.from, range.to, withTotal = true)(_.richTask)
  )
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, TaskSteps](
    "getTask",
    FieldsParser[IdOrName],
    (param, graph, authContext) => taskSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val outputQuery: Query = Query.output[RichTask, TaskSteps](_.richTask)
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[TaskSteps, UserSteps]("assignableUsers", (taskSteps, authContext) => taskSteps.assignableUsers(authContext)),
    Query[TaskSteps, LogSteps]("logs", (taskSteps, _) => taskSteps.logs),
    Query[TaskSteps, CaseSteps]("case", (taskSteps, _) => taskSteps.`case`),
    Query[TaskSteps, OrganisationSteps]("organisations", (taskSteps, authContext) => taskSteps.organisations.visible(authContext))
  )

  def create: Action[AnyContent] =
    entrypoint("create task")
      .extract("task", FieldsParser[InputTask])
      .extract("caseId", FieldsParser[String])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputTask: InputTask = request.body("task")
        val caseId: String       = request.body("caseId")
        for {
          case0        <- caseSrv.getOrFail(caseId)
          createdTask  <- taskSrv.create(inputTask.toTask, None)
          organisation <- organisationSrv.getOrFail(request.organisation)
          _            <- shareSrv.shareTask(createdTask, case0, organisation)
        } yield Results.Created(createdTask.toJson)
      }

  def get(taskId: String): Action[AnyContent] =
    entrypoint("get task")
      .authRoTransaction(db) { implicit request => implicit graph =>
        taskSrv
          .getByIds(taskId)
          .visible
          .richTask
          .getOrFail("Task")
          .map(task => Results.Ok(task.toJson))
      }

  def list: Action[AnyContent] =
    entrypoint("list task")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val tasks = taskSrv
          .initSteps
          .visible
          .richTask
          .toList
        Success(Results.Ok(tasks.toJson))
      }

  def update(taskId: String): Action[AnyContent] =
    entrypoint("update task")
      .extract("task", FieldsParser.update("task", properties.task))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("task")
        taskSrv
          .update(
            _.getByIds(taskId)
              .can(Permissions.manageTask),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }
}
