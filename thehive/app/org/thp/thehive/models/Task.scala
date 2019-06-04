package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph._
import org.thp.scalligraph.models.Entity

object TaskStatus extends Enumeration {
  val waiting, inProgress, completed, cancel = Value
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
