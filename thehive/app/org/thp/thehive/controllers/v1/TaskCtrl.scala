package org.thp.thehive.controllers.v1

import scala.util.{Failure, Success}

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.AuthorizationError
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.InputTask
import org.thp.thehive.services.{CaseSrv, TaskSrv}

@Singleton
class TaskCtrl @Inject()(entryPoint: EntryPoint, db: Database, taskSrv: TaskSrv, caseSrv: CaseSrv) extends TaskConversion {

  def create: Action[AnyContent] =
    entryPoint("create task")
      .extract('task, FieldsParser[InputTask])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val inputTask: InputTask = request.body('task)
          caseSrv
            .getOrFail(inputTask.caseId)
            .map { `case` ⇒
              val createdTask = taskSrv.create(inputTask, `case`)
              Results.Created(createdTask.toJson)
            }
        }
      }

  def get(taskId: String): Action[AnyContent] =
    entryPoint("get task")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          taskSrv
            .get(taskId)
            .availableFor(request.organisation)
            .getOrFail()
            .map(task ⇒ Results.Ok(task.toJson))
        }
      }

  def list: Action[AnyContent] =
    entryPoint("list task")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val tasks = taskSrv.initSteps
            .availableFor(request.organisation)
            .toList()
            .map(_.toJson)
          Success(Results.Ok(Json.toJson(tasks)))
        }
      }

  def update(taskId: String): Action[AnyContent] =
    entryPoint("update task")
      .extract('task, UpdateFieldsParser[InputTask])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          if (taskSrv.isAvailableFor(taskId)) {
            taskSrv.update(taskId, outputTaskProperties, request.body('task))
            Success(Results.NoContent)
          } else Failure(AuthorizationError(s"Task $taskId doesn't exist or permission is insufficient"))
        }
      }
}
