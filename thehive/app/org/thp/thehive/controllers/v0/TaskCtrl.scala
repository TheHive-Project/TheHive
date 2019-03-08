package org.thp.thehive.controllers.v0

import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.Query
import org.thp.thehive.dto.v0.InputTask
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, TaskSrv}

@Singleton
class TaskCtrl @Inject()(apiMethod: ApiMethod, db: Database, taskSrv: TaskSrv, caseSrv: CaseSrv, val queryExecutor: TheHiveQueryExecutor)
    extends QueryCtrl {

  lazy val logger = Logger(getClass)

  def create: Action[AnyContent] =
    apiMethod("create task")
      .extract('task, FieldsParser[InputTask])
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val inputTask: InputTask = request.body('task)
          val `case`               = caseSrv.getOrFail(inputTask.caseId)
          val createdTask          = taskSrv.create(inputTask, `case`)
          Results.Created(createdTask.toJson)
        }
      }

  def get(taskId: String): Action[AnyContent] =
    apiMethod("get task")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val task = taskSrv
            .get(taskId)
            .availableFor(request.organisation)
            .getOrFail()
          Results.Ok(task.toJson)
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list task")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val tasks = taskSrv.initSteps
            .availableFor(request.organisation)
            .toList()
            .map(_.toJson)
          Results.Ok(Json.toJson(tasks))
        }
      }

  def update(taskId: String): Action[AnyContent] =
    apiMethod("update task")
      .extract('task, UpdateFieldsParser[InputTask])
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (taskSrv.isAvailableFor(taskId)) {
            taskSrv.update(taskId, outputTaskProperties(db), request.body('task))
            Results.NoContent
          } else Results.Unauthorized(s"Task $taskId doesn't exist or permission is insufficient")
        }
      }

  def stats(): Action[AnyContent] = {
    val parser: FieldsParser[Seq[Query]] = statsParser("listTask")
    apiMethod("stats task")
      .extract('query, parser)
      .requires(Permissions.read) { implicit request ⇒
        val queries: Seq[Query] = request.body('query)
        val results = queries
          .map { query ⇒
            db.transaction { graph ⇒
              queryExecutor.execute(query, graph, Some(request.authContext)).toJson
            }
          }
          .foldLeft(JsObject.empty) {
            case (acc, o: JsObject) ⇒ acc ++ o
            case (acc, r) ⇒
              logger.warn(s"Invalid stats result: $r")
              acc
          }
        Results.Ok(results)
      }
  }
}
