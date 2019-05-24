package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph._

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
    dueDate: Option[Date])
