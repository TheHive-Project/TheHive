package org.thp.thehive.controllers.v1

import java.util.Date

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v1.{InputTask, OutputTask}
import org.thp.thehive.models.{Task, TaskStatus}
import org.thp.thehive.services.TaskSteps

trait TaskConversion {
  implicit def fromInputTask(inputTask: InputTask): Task =
    inputTask
      .into[Task]
      .withFieldComputed(_.status, _.status.fold(TaskStatus.waiting)(TaskStatus.withName))
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
      .property[String]("title")(_.simple.updatable)
      .property[Option[String]]("description")(_.simple.updatable)
      .property[String]("status")(_.simple.updatable)
      .property[Boolean]("flag")(_.simple.updatable)
      .property[Option[Date]]("startDate")(_.simple.updatable)
      .property[Option[Date]]("endDate")(_.simple.updatable)
      .property[Int]("order")(_.simple.updatable)
      .property[Option[Date]]("dueDate")(_.simple.updatable)
      .build

}
