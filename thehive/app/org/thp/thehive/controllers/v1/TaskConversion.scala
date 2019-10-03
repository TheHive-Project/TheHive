package org.thp.thehive.controllers.v1

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v1.{InputTask, OutputTask}
import org.thp.thehive.models.{RichTask, Task, TaskStatus}
import org.thp.thehive.services.TaskSteps

object TaskConversion {
  implicit def fromInputTask(inputTask: InputTask): Task =
    inputTask
      .into[Task]
      .withFieldComputed(_.status, _.status.fold(TaskStatus.Waiting)(TaskStatus.withName))
      .withFieldComputed(_.order, _.order.getOrElse(0))
      .withFieldComputed(_.group, _.group.getOrElse("default"))
      .transform

  implicit def toOutputTask(task: RichTask): Output[OutputTask] =
    Output[OutputTask](
      task
        .into[OutputTask]
        .withFieldComputed(_.status, _.status.toString)
        .transform
    )

  def taskProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[TaskSteps]
      .property("title", UniMapping.string)(_.field.updatable)
      .property("description", UniMapping.string.optional)(_.field.updatable)
      .property("status", UniMapping.string)(_.field.updatable)
      .property("flag", UniMapping.boolean)(_.field.updatable)
      .property("startDate", UniMapping.date.optional)(_.field.updatable)
      .property("endDate", UniMapping.date.optional)(_.field.updatable)
      .property("order", UniMapping.int)(_.field.updatable)
      .property("dueDate", UniMapping.date.optional)(_.field.updatable)
      .build

}
