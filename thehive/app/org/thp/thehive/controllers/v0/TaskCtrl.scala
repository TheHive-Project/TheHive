package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.RichOptionTry
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputTask
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services._
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class TaskCtrl @Inject() (
    entrypoint: Entrypoint,
    @Named("with-thehive-schema") db: Database,
    properties: Properties,
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv,
    shareSrv: ShareSrv
) extends QueryableCtrl {

  lazy val logger: Logger                                   = Logger(getClass)
  override val entityName: String                           = "task"
  override val publicProperties: List[PublicProperty[_, _]] = properties.task
  override val initialQuery: Query =
    Query.init[Traversal.V[Task]]("listTask", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.tasks)
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Task], IteratorOutput](
    "page",
    FieldsParser[OutputParam], {
      case (OutputParam(from, to, _, 0), taskSteps, _) =>
        taskSteps.richPage(from, to, withTotal = true)(_.richTask.domainMap(_ -> (None: Option[RichCase])))
      case (OutputParam(from, to, _, _), taskSteps, authContext) =>
        taskSteps.richPage(from, to, withTotal = true)(
          _.richTaskWithCustomRenderer(_.`case`.richCase(authContext).domainMap(c => Some(c): Option[RichCase]))
        )
    }
  )
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, Traversal.V[Task]](
    "getTask",
    FieldsParser[IdOrName],
    (param, graph, authContext) => taskSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val outputQuery: Query = Query.output[RichTask, Traversal.V[Task]](_.richTask)
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query.output[(RichTask, Option[RichCase])],
    Query[Traversal.V[Task], Traversal.V[User]]("assignableUsers", (taskSteps, authContext) => taskSteps.assignableUsers(authContext))
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
          .getOrFail("Task")
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
                .getOrFail("Task")
                .map(richTask => Results.Ok(richTask.toJson))
          }
      }
}
