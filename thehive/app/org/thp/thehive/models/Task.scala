package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph._
import org.thp.scalligraph.models.Entity
import play.api.libs.json.{Format, Json, OFormat}

object TaskStatus extends Enumeration {
  type Type = Value

  val Waiting, InProgress, Completed, Cancel = Value

  implicit val format: Format[TaskStatus.Type] = Json.formatEnum(TaskStatus)
}

@EdgeEntity[Task, User]
case class TaskUser()

@EdgeEntity[Task, Log]
case class TaskLog()

@VertexEntity
case class Task(
    title: String,
    group: Option[String],
    description: Option[String],
    status: TaskStatus.Value,
    flag: Boolean,
    startDate: Option[Date],
    endDate: Option[Date],
    order: Int,
    dueDate: Option[Date]
)

object Task {
  implicit val format: OFormat[Task] = Json.format[Task]
}

case class RichTask(
    task: Task with Entity,
    owner: Option[String]
) {
  val _id: String                 = task._id
  val _createdBy: String          = task._createdBy
  val _updatedBy: Option[String]  = task._updatedBy
  val _createdAt: Date            = task._createdAt
  val _updatedAt: Option[Date]    = task._updatedAt
  val title: String               = task.title
  val group: Option[String]       = task.group
  val description: Option[String] = task.description
  val status: TaskStatus.Value    = task.status
  val flag: Boolean               = task.flag
  val startDate: Option[Date]     = task.startDate
  val endDate: Option[Date]       = task.endDate
  val order: Int                  = task.order
  val dueDate: Option[Date]       = task.dueDate
}
