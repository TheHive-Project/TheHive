package org.thp.thehive.controllers.v1

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.models.{Entity, UniMapping}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v1.{InputTask, OutputTask}
import org.thp.thehive.models.{Task, TaskStatus}
import org.thp.thehive.services.TaskSteps
import scala.language.implicitConversions

import org.thp.scalligraph.controllers.Output

object TaskConversion {
  implicit def fromInputTask(inputTask: InputTask): Task =
    inputTask
      .into[Task]
      .withFieldComputed(_.status, _.status.fold(TaskStatus.Waiting)(TaskStatus.withName))
      .withFieldComputed(_.order, _.order.getOrElse(0))
      .transform

  implicit def toOutputTask(task: Task with Entity): Output[OutputTask] =
    Output[OutputTask](
      task
        .asInstanceOf[Task]
        .into[OutputTask]
        .withFieldComputed(_.status, _.status.toString)
        .transform
    )

  def taskProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[TaskSteps]
      .property("title", UniMapping.string)(_.simple.updatable)
      .property("description", UniMapping.string.optional)(_.simple.updatable)
      .property("status", UniMapping.string)(_.simple.updatable)
      .property("flag", UniMapping.boolean)(_.simple.updatable)
      .property("startDate", UniMapping.date.optional)(_.simple.updatable)
      .property("endDate", UniMapping.date.optional)(_.simple.updatable)
      .property("order", UniMapping.int)(_.simple.updatable)
      .property("dueDate", UniMapping.date.optional)(_.simple.updatable)
      .build

}
