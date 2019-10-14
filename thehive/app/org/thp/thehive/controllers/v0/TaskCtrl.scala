package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.RichOptionTry
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputTask, OutputTask}
import org.thp.thehive.models.{Permissions, RichTask}
import org.thp.thehive.services._
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class TaskCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    properties: Properties,
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv,
    shareSrv: ShareSrv
) extends QueryableCtrl {

  lazy val logger                                           = Logger(getClass)
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
  override val outputQuery: Query = Query.output[RichTask, OutputTask](_.toOutput)
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[TaskSteps, List[RichTask]]("toList", (taskSteps, _) => taskSteps.richTask.toList)
  )

  def create(caseId: String): Action[AnyContent] =
    entryPoint("create task")
      .extract("task", FieldsParser[InputTask])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputTask: InputTask = request.body("task")
        for {
          case0       <- caseSrv.getOrFail(caseId)
          createdTask <- taskSrv.create(inputTask.toTask)
          _           <- shareSrv.shareCaseTask(case0, createdTask)
          owner       <- inputTask.owner.map(userSrv.getOrFail).flip
          _           <- owner.map(taskSrv.assign(createdTask, _)).flip
          richTask = RichTask(createdTask, owner.map(_.login))
        } yield Results.Created(richTask.toJson)
      }

  def get(taskId: String): Action[AnyContent] =
    entryPoint("get task")
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
    entryPoint("update task")
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
