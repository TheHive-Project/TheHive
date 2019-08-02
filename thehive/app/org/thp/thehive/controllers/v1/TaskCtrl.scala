package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.thehive.dto.v1.InputTask
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{CaseSrv, TaskSrv}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class TaskCtrl @Inject()(entryPoint: EntryPoint, db: Database, taskSrv: TaskSrv, caseSrv: CaseSrv) {

  import TaskConversion._

  def create: Action[AnyContent] =
    entryPoint("create task")
      .extract("task", FieldsParser[InputTask])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputTask: InputTask = request.body("task")
        for {
          case0       <- caseSrv.getOrFail(inputTask.caseId)
          createdTask <- taskSrv.create(inputTask)
          _           <- caseSrv.addTask(case0, createdTask)
        } yield Results.Created(createdTask.toJson)
      }

  def get(taskId: String): Action[AnyContent] =
    entryPoint("get task")
      .authRoTransaction(db) { implicit request => implicit graph =>
        taskSrv
          .get(taskId)
          .visible
          .getOrFail()
          .map(task => Results.Ok(task.toJson))
      }

  def list: Action[AnyContent] =
    entryPoint("list task")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val tasks = taskSrv
          .initSteps
          .visible
          .toList
          .map(_.toJson)
        Success(Results.Ok(Json.toJson(tasks)))
      }

  def update(taskId: String): Action[AnyContent] =
    entryPoint("update task")
      .extract("task", FieldsParser.update("task", taskProperties))
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
