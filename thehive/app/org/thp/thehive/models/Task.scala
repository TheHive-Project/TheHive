package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph._

object TaskStatus extends Enumeration {
  val waiting, inProgress, completed, cancel = Value
}

@EdgeEntity[Task, Case]
case class TaskCase()

@EdgeEntity[Task, CaseTemplate]
case class TaskCaseTemplate()

@EdgeEntity[Task, User]
case class TaskUser()

@VertexEntity
case class Task(
    title: String,
    description: Option[String],
    status: TaskStatus.Value,
    flag: Boolean,
    startDate: Option[Date],
    endDate: Option[Date],
    order: Int,
    dueDate: Option[Date])
