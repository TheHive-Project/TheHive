package org.thp.thehive.controllers.v0

import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, IndexType, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.scalligraph.{EntityIdOrName, RichOptionTry}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputTask
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

class TaskCtrl(
    override val entrypoint: Entrypoint,
    override val db: Database,
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    override val organisationSrv: OrganisationSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val customFieldValueSrv: CustomFieldValueSrv,
    override val queryExecutor: QueryExecutor,
    override val publicData: PublicTask
) extends QueryCtrl
    with TheHiveOps {

  def create(caseId: String): Action[AnyContent] =
    entrypoint("create task")
      .extract("task", FieldsParser[InputTask])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputTask: InputTask = request.body("task")
        for {
          case0       <- caseSrv.get(EntityIdOrName(caseId)).can(Permissions.manageTask).getOrFail("Case")
          createdTask <- caseSrv.createTask(case0, inputTask.toTask)
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
            _.get(EntityIdOrName(taskId))
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

  def searchInCase(caseId: String): Action[AnyContent] = {
    val query = Query.init[Traversal.V[Task]](
      "tasksInCase",
      (graph, authContext) =>
        caseSrv
          .get(EntityIdOrName(caseId))(graph)
          .visible(authContext)
          ._id
          .headOption
          .fold[Traversal.V[Task]](graph.empty)(c => taskSrv.startTraversal(graph).relatedTo(c))
    )
    entrypoint("search task in case")
      .extract("query", searchParser(query))
      .auth { implicit request =>
        val query: Query = request.body("query")
        queryExecutor.execute(query, request)
      }
  }
}

class PublicTask(
    taskSrv: TaskSrv,
    userSrv: UserSrv,
    searchSrv: SearchSrv,
    override val organisationSrv: OrganisationSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val customFieldValueSrv: CustomFieldValueSrv
) extends PublicData
    with TheHiveOps {
  override val entityName: String = "task"
  override val initialQuery: Query =
    Query.init[Traversal.V[Task]](
      "listTask",
      (graph, authContext) => taskSrv.startTraversal(graph).visible(authContext)
    )
  //organisationSrv.get(authContext.organisation)(graph).shares.tasks)
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Task], IteratorOutput](
    "page",
    {
      case (OutputParam(from, to, _, 0), taskSteps, _) =>
        taskSteps.richPage(from, to, withTotal = true)(_.richTask.domainMap(_ -> (None: Option[RichCase])))
      case (OutputParam(from, to, _, _), taskSteps, authContext) =>
        taskSteps.richPage(from, to, withTotal = true)(
          _.richTaskWithCustomRenderer(_.`case`.richCase(authContext).option)
        )
    }
  )
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Task]](
    "getTask",
    (idOrName, graph, authContext) => taskSrv.get(idOrName)(graph).visible(authContext)
  )
  override val outputQuery: Query =
    Query.outputWithContext[RichTask, Traversal.V[Task]]((taskSteps, _) => taskSteps.richTask)
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query.output[(RichTask, Option[RichCase])],
    Query[Traversal.V[Task], Traversal.V[User]]("assignableUsers", (taskSteps, authContext) => taskSteps.assignableUsers(authContext)),
    Query.init[Traversal.V[Task]](
      "waitingTasks",
      (graph, authContext) => taskSrv.startTraversal(graph).has(_.status, TaskStatus.Waiting).visible(authContext)
    ),
    Query.init[Traversal.V[Task]]( // DEPRECATED
      "waitingTask",
      (graph, authContext) => taskSrv.startTraversal(graph).has(_.status, TaskStatus.Waiting).visible(authContext)
    ),
    Query.init[Traversal.V[Task]](
      "myTasks",
      (graph, authContext) =>
        taskSrv
          .startTraversal(graph)
          .assignTo(authContext.userId)
          .visible(authContext)
    ),
    Query[Traversal.V[Task], Traversal.V[Log]]("logs", (taskSteps, _) => taskSteps.logs),
    Query[Traversal.V[Task], Traversal.V[Case]]("case", (taskSteps, _) => taskSteps.`case`),
    Query[Traversal.V[Task], Traversal.V[CaseTemplate]]("caseTemplate", (taskSteps, _) => taskSteps.caseTemplate),
    Query[Traversal.V[Task], Traversal.V[Organisation]]("organisations", (taskSteps, authContext) => taskSteps.organisations.visible(authContext))
  )
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[Task]
    .property("keyword", UMapping.string)(
      _.select(_.empty.asInstanceOf[Traversal[String, _, _]])
        .filter[String](IndexType.fulltext) {
          case (_, t, _, Right(p))   => searchSrv("Task", p.getValue)(t)
          case (_, t, _, Left(true)) => t
          case (_, t, _, _)          => t.empty
        }
        .readonly
    )
    .property("title", UMapping.string)(_.field.updatable)
    .property("description", UMapping.string.optional)(_.field.updatable)
    .property("status", UMapping.enum[TaskStatus.type])(_.field.custom { (_, value, vertex, graph, authContext) =>
      for {
        task <- taskSrv.get(vertex)(graph).getOrFail("Task")
        _    <- taskSrv.updateStatus(task, value)(graph, authContext)
      } yield Json.obj("status" -> value)
    })
    .property("flag", UMapping.boolean)(_.field.updatable)
    .property("startDate", UMapping.date.optional)(_.field.updatable)
    .property("endDate", UMapping.date.optional)(_.field.updatable)
    .property("order", UMapping.int)(_.field.updatable)
    .property("dueDate", UMapping.date.optional)(_.field.updatable)
    .property("group", UMapping.string)(_.field.updatable)
    .property("owner", UMapping.string.optional)(
      _.rename("assignee")
        .custom { (_, login: Option[String], vertex, graph, authContext) =>
          for {
            task <- taskSrv.get(vertex)(graph).getOrFail("Task")
            user <- login.map(l => userSrv.getOrFail(EntityIdOrName(l))(graph)).flip
            _ <- user match {
              case Some(u) => taskSrv.assign(task, u)(graph, authContext)
              case None    => taskSrv.unassign(task)(graph, authContext)
            }
          } yield Json.obj("owner" -> user.map(_.login))
        }
    )
    .build
}
