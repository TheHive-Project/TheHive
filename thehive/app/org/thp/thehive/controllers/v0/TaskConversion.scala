package org.thp.thehive.controllers.v0

import scala.language.implicitConversions

import play.api.libs.json.Json

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.RichOptionTry
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.{Entity, UniMapping}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputTask, OutputTask}
import org.thp.thehive.models.{RichTask, Task, TaskStatus}
import org.thp.thehive.services.{TaskSrv, TaskSteps, UserSrv}

object TaskConversion {

  implicit def fromInputTask(inputTask: InputTask): Task =
    inputTask
      .into[Task]
      .withFieldComputed(_.status, _.status.fold(TaskStatus.Waiting)(TaskStatus.withName))
      .withFieldComputed(_.order, _.order.getOrElse(0))
      .withFieldComputed(_.flag, _.flag.getOrElse(false))
      .withFieldComputed(_.group, _.group.getOrElse("default"))
      .transform

  def toOutputTask(task: Task with Entity): Output[OutputTask] = toOutputTask(RichTask(task, None))

  implicit def toOutputTask(richTask: RichTask): Output[OutputTask] =
    Output[OutputTask](
      richTask
        .into[OutputTask]
        .withFieldConst(_.id, richTask._id)
        .withFieldComputed(_.status, _.status.toString)
        .withFieldConst(_._type, "case_task")
        .withFieldRenamed(_._updatedAt, _.updatedAt)
        .withFieldRenamed(_._updatedBy, _.updatedBy)
        .withFieldRenamed(_._createdAt, _.createdAt)
        .withFieldRenamed(_._createdBy, _.createdBy)
        .transform
    )

  def taskProperties(taskSrv: TaskSrv, userSrv: UserSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[TaskSteps]
      .property("title", UniMapping.string)(_.simple.updatable)
      .property("description", UniMapping.string.optional)(_.simple.updatable)
      .property("status", UniMapping.enum(TaskStatus))(_.simple.custom { (_, value, vertex, _, graph, authContext) =>
        for {
          task <- taskSrv.get(vertex)(graph).getOrFail()
          user <- userSrv
            .current(graph, authContext)
            .getOrFail()
          _ <- taskSrv.updateStatus(task, user, value)(graph, authContext)
        } yield Json.obj("status" -> value)
      })
      .property("flag", UniMapping.boolean)(_.simple.updatable)
      .property("startDate", UniMapping.date.optional)(_.simple.updatable)
      .property("endDate", UniMapping.date.optional)(_.simple.updatable)
      .property("order", UniMapping.int)(_.simple.updatable)
      .property("dueDate", UniMapping.date.optional)(_.simple.updatable)
      .property("owner", UniMapping.string.optional)(
        _.derived(_.user.login)
          .custom { (_, login: Option[String], vertex, _, graph, authContext) =>
            for {
              task <- taskSrv.get(vertex)(graph).getOrFail()
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
