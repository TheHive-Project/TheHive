package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.thehive.dto.v0.{InputTask, OutputTask}
import org.thp.thehive.models.{Permissions, RichTask}
import org.thp.thehive.services._
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class TaskCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv
) extends QueryableCtrl {
  import TaskConversion._

  lazy val logger                                           = Logger(getClass)
  override val entityName: String                           = "log"
  override val publicProperties: List[PublicProperty[_, _]] = taskProperties(taskSrv, userSrv) ::: metaProperties[TaskSteps]
  override val initialQuery: Query =
    Query.init[TaskSteps]("listTask", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.tasks)
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, TaskSteps, PagedResult[RichTask]](
    "page",
    FieldsParser[OutputParam],
    (range, taskSteps, _) => taskSteps.richPage(range.from, range.to, withTotal = true)(_.richTask.raw)
  )
  override val outputQuery: Query = Query.output[RichTask, OutputTask]

  def create(caseId: String): Action[AnyContent] =
    entryPoint("create task")
      .extract("task", FieldsParser[InputTask])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputTask: InputTask = request.body("task")
        for {
          case0       <- caseSrv.getOrFail(caseId)
          createdTask <- taskSrv.create(inputTask, case0)
          owner       <- inputTask.owner.map(userSrv.getOrFail).flip
          _        = owner.foreach(taskSrv.assign(createdTask, _))
          richTask = RichTask(createdTask, owner.map(_.login))
        } yield Results.Created(richTask.toJson)
      }

  def get(taskId: String): Action[AnyContent] =
    entryPoint("get task")
      .authTransaction(db) { implicit request => implicit graph =>
        taskSrv
          .get(taskId)
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
            _.get(taskId)
              .can(Permissions.manageTask),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }
}
