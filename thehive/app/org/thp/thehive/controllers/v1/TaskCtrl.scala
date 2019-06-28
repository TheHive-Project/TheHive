package org.thp.thehive.controllers.v1

import scala.util.Success

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.thehive.dto.v1.InputTask
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{CaseSrv, TaskSrv}

@Singleton
class TaskCtrl @Inject()(entryPoint: EntryPoint, db: Database, taskSrv: TaskSrv, caseSrv: CaseSrv) extends TaskConversion {

  def create: Action[AnyContent] =
    entryPoint("create task")
      .extract('task, FieldsParser[InputTask])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputTask: InputTask = request.body('task)
        for {
          case0       <- caseSrv.getOrFail(inputTask.caseId)
          createdTask <- taskSrv.create(inputTask, case0)
        } yield Results.Created(createdTask.toJson)
      }

  def get(taskId: String): Action[AnyContent] =
    entryPoint("get task")
      .authTransaction(db) { implicit request => implicit graph =>
        taskSrv
          .get(taskId)
          .availableFor(request.organisation)
          .getOrFail()
          .map(task => Results.Ok(task.toJson))
      }

  def list: Action[AnyContent] =
    entryPoint("list task")
      .authTransaction(db) { implicit request => implicit graph =>
        val tasks = taskSrv
          .initSteps
          .availableFor(request.organisation)
          .toList()
          .map(_.toJson)
        Success(Results.Ok(Json.toJson(tasks)))
      }

  def update(taskId: String): Action[AnyContent] =
    entryPoint("update task")
      .extract('task, FieldsParser.update("task", taskProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body('task)
        taskSrv
          .update(
            _.get(taskId)
              .can(Permissions.manageTask),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }
}
