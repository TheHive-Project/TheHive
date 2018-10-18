package org.thp.thehive.controllers.v1

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.InputTask
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, TaskSrv}

@Singleton
class TaskCtrl @Inject()(apiMethod: ApiMethod, db: Database, taskSrv: TaskSrv, caseSrv: CaseSrv) {

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
            .getOrFail(taskId)
          Results.Ok(task.toJson)
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list task")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val tasks = taskSrv.initSteps.toList.map(_.toJson)
          Results.Ok(Json.toJson(tasks))
        }
      }

  def update(taskId: String): Action[AnyContent] =
    apiMethod("update task")
      .extract('task, UpdateFieldsParser[InputTask])
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          taskSrv.update(taskId, request.body('task))
          Results.NoContent
        }
      }
}
