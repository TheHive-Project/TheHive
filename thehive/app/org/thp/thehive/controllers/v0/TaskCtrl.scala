package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.RichOptionTry
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputTask
import org.thp.thehive.models.{Permissions, RichCase, RichTask}
import org.thp.thehive.services._
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class TaskCtrl @Inject() (
    entrypoint: Entrypoint,
    db: Database,
    properties: Properties,
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv,
    shareSrv: ShareSrv
) extends QueryableCtrl {

  lazy val logger: Logger                                   = Logger(getClass)
  override val entityName: String                           = "task"
  override val publicProperties: List[PublicProperty[_, _]] = properties.task ::: metaProperties[TaskSteps]
  override val initialQuery: Query =
    Query.init[TaskSteps]("listTask", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.tasks)
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, TaskSteps, PagedResult[(RichTask, Option[RichCase])]](
    "page",
    FieldsParser[OutputParam], {
      case (OutputParam(from, to, _, 0), taskSteps, _) => taskSteps.richPage(from, to, withTotal = true)(_.richTask.map(_ -> None))
      case (OutputParam(from, to, _, _), taskSteps, authContext) =>
        taskSteps.richPage(from, to, withTotal = true)(_.richTaskWithCustomRenderer(_.`case`.richCase(authContext).map(c => Option(c)))(authContext))
    }
  )
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, TaskSteps](
    "getTask",
    FieldsParser[IdOrName],
    (param, graph, authContext) => taskSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val outputQuery: Query = Query.output[RichTask, TaskSteps](_.richTask)
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query.output[(RichTask, Option[RichCase])],
    Query[TaskSteps, UserSteps]("assignableUsers", (taskSteps, authContext) => taskSteps.assignableUsers(authContext))
  )

  def create(caseId: String): Action[AnyContent] =
    entrypoint("create task")
      .extract("task", FieldsParser[InputTask])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputTask: InputTask = request.body("task")
        for {
          case0        <- caseSrv.getOrFail(caseId)
          owner        <- inputTask.owner.map(userSrv.getOrFail).flip
          createdTask  <- taskSrv.create(inputTask.toTask, owner)
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
          .getOrFail()
          .map { task =>
            Results.Ok(task.toJson)
          }
      }

  def update(taskId: String): Action[AnyContent] =
    entrypoint("update task")
      .extract("task", FieldsParser.update("task", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("task")
        taskSrv
          .update(
            _.getByIds(taskId)
              .can(Permissions.manageTask),
            propertyUpdaters
          )
          .flatMap {
            case (taskSteps, _) =>
              taskSteps
                .richTask
                .getOrFail()
                .map(richTask => Results.Ok(richTask.toJson))
          }
      }
}
