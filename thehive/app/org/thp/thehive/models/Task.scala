package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph._

object TaskStatus extends Enumeration {
  val waiting, inProgress, completed, cancel = Value
}

@EdgeEntity[Task, Log]
case class TaskLog()

@EdgeEntity[Task, User]
case class TaskUser()

@VertexEntity
case class Task(
    title: String,
    description: String,
    status: TaskStatus.Value,
    flag: Boolean,
    startDate: Date,
    endDate: Option[Date],
    order: Int,
    dueDate: Option[Date])
