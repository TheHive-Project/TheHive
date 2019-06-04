package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{PropertyUpdater, Query}
import org.thp.thehive.dto.v0.InputTask
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{CaseSrv, TaskSrv, UserSrv}
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}
import scala.util.Success

@Singleton
class TaskCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    val taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    val userSrv: UserSrv,
    val queryExecutor: TheHiveQueryExecutor
) extends QueryCtrl
    with TaskConversion {

  lazy val logger = Logger(getClass)

  def create(caseId: String): Action[AnyContent] =
    entryPoint("create task")
      .extract('task, FieldsParser[InputTask])
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        val inputTask: InputTask = request.body('task)
        caseSrv.getOrFail(caseId).map { `case` ⇒
          val createdTask = taskSrv.create(inputTask, `case`)
          Results.Created(createdTask.toJson)
        }
      }

  def get(taskId: String): Action[AnyContent] =
    entryPoint("get task")
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        taskSrv
          .get(taskId)
          .visible
          .getOrFail()
          .map { task ⇒
            Results.Ok(task.toJson)
          }
      }

  def list: Action[AnyContent] =
    entryPoint("list task")
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        val tasks = taskSrv
          .initSteps
          .visible
          .toList()
          .map(_.toJson)
        Success(Results.Ok(Json.toJson(tasks)))
      }

  def update(taskId: String): Action[AnyContent] =
    entryPoint("update task")
      .extract('task, FieldsParser.update("task", taskProperties))
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        val propertyUpdaters: Seq[PropertyUpdater] = request.body('task)
        taskSrv
          .update(
            _.get(taskId)
              .can(Permissions.manageTask),
            propertyUpdaters
          )
          .map(_ ⇒ Results.NoContent)
      }

  def stats(): Action[AnyContent] = {
    val parser: FieldsParser[Seq[Query]] = statsParser("listTask")
    entryPoint("stats task")
      .extract('query, parser)
      .authTransaction(db) { implicit request ⇒ graph ⇒
        val queries: Seq[Query] = request.body('query)
        val results = queries
          .map(query ⇒ queryExecutor.execute(query, graph, request.authContext).toJson)
          .foldLeft(JsObject.empty) {
            case (acc, o: JsObject) ⇒ acc ++ o
            case (acc, r) ⇒
              logger.warn(s"Invalid stats result: $r")
              acc
          }
        Success(Results.Ok(results))
      }
  }

  def search: Action[AnyContent] =
    entryPoint("search case")
      .extract('query, searchParser("listTask", paged = false))
      .authTransaction(db) { implicit request ⇒ graph ⇒
        val query: Query = request.body('query)
        val result       = queryExecutor.execute(query, graph, request.authContext)
        val resp         = Results.Ok((result.toJson \ "result").as[JsValue])
        result.toOutput match {
          case PagedResult(_, Some(size)) ⇒ Success(resp.withHeaders("X-Total" → size.toString))
          case _                          ⇒ Success(resp)
        }
      }
}
