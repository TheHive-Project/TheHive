package org.thp.thehive.controllers.v0
import java.util.Date

import gremlin.scala.Vertex
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PublicProperty.readonly
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputTask, OutputTask}
import org.thp.thehive.models.{Task, TaskStatus, TaskUser}
import org.thp.thehive.services.TaskSteps
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.services._
import scala.language.implicitConversions

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
        .withFieldConst(_.id, task._id)
        .withFieldConst(_._id, task._id)
        .withFieldComputed(_.status, _.status.toString)
        .transform
    )

  def outputTaskProperties(implicit db: Database): List[PublicProperty[Vertex, _, _]] =
    // format: off
    PublicPropertyListBuilder[TaskSteps, Vertex]
      .property[String]("title").simple
      .property[Option[String]]("description").simple
      .property[String]("status").simple
      .property[Boolean]("flag").simple
      .property[Option[Date]]("startDate").simple
      .property[Option[Date]]("endDate").simple
      .property[Int]("order").simple
      .property[Option[Date]]("dueDate").simple
      .property[Option[String]]("owner").derived(_.outTo[TaskUser].value("login"))(readonly)
      .build
  // format: on
}
