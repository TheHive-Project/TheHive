package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.RichOptionTry
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query._
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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class TaskCtrl @Inject() (
    override val entrypoint: Entrypoint,
    @Named("with-thehive-schema") override val db: Database,
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv,
    shareSrv: ShareSrv,
    override val queryExecutor: QueryExecutor,
    override val publicData: PublicTask
) extends QueryCtrl {

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
      .extract("task", FieldsParser.update("task", publicData.publicProperties))
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

//  def searchInCase(caseId: String): Action[AnyContent] =
//    entrypoint("search task in case")
//      .extract("query", searchParser)
//      .auth { implicit request =>
//        val query: Query = request.body("query")
//        queryExecutor.execute(query, request)
//      }
}

@Singleton
class PublicTask @Inject() (taskSrv: TaskSrv, organisationSrv: OrganisationSrv, userSrv: UserSrv) extends PublicData {
  override val entityName: String = "task"
  override val initialQuery: Query =
    Query.init[Traversal.V[Task]]("listTask", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.tasks)
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Task], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    {
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
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[Task]
    .property("title", UMapping.string)(_.field.updatable)
    .property("description", UMapping.string.optional)(_.field.updatable)
    .property("status", UMapping.enum[TaskStatus.type])(_.field.custom { (_, value, vertex, _, graph, authContext) =>
      for {
        task <- taskSrv.get(vertex)(graph).getOrFail("Task")
        user <-
          userSrv
            .current(graph, authContext)
            .getOrFail("User")
        _ <- taskSrv.updateStatus(task, user, value)(graph, authContext)
      } yield Json.obj("status" -> value)
    })
    .property("flag", UMapping.boolean)(_.field.updatable)
    .property("startDate", UMapping.date.optional)(_.field.updatable)
    .property("endDate", UMapping.date.optional)(_.field.updatable)
    .property("order", UMapping.int)(_.field.updatable)
    .property("dueDate", UMapping.date.optional)(_.field.updatable)
    .property("group", UMapping.string)(_.field.updatable)
    .property("owner", UMapping.string.optional)(
      _.select(_.assignee.value(_.login))
        .custom { (_, login: Option[String], vertex, _, graph, authContext) =>
          for {
            task <- taskSrv.get(vertex)(graph).getOrFail("Task")
            user <- login.map(userSrv.getOrFail(_)(graph)).flip
            _ <- user match {
              case Some(u) => taskSrv.assign(task, u)(graph, authContext)
              case None    => taskSrv.unassign(task)(graph, authContext)
            }
          } yield Json.obj("owner" -> user.map(_.login))
        }
    )
    .build

}
