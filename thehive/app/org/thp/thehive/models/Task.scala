package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph._
import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}
import play.api.libs.json.{Format, Json}

object TaskStatus extends Enumeration {
  val Waiting, InProgress, Completed, Cancel = Value

  implicit val format: Format[Value] = Json.formatEnum(TaskStatus)
}

@BuildEdgeEntity[Task, User]
case class TaskUser()

@BuildEdgeEntity[Task, Log]
case class TaskLog()

@BuildVertexEntity
@DefineIndex(IndexType.basic, "status")
case class Task(
    title: String,
    group: String,
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
    assignee: Option[User with Entity]
) {
  def _id: EntityId               = task._id
  def _createdBy: String          = task._createdBy
  def _updatedBy: Option[String]  = task._updatedBy
  def _createdAt: Date            = task._createdAt
  def _updatedAt: Option[Date]    = task._updatedAt
  def title: String               = task.title
  def group: String               = task.group
  def description: Option[String] = task.description
  def status: TaskStatus.Value    = task.status
  def flag: Boolean               = task.flag
  def startDate: Option[Date]     = task.startDate
  def endDate: Option[Date]       = task.endDate
  def order: Int                  = task.order
  def dueDate: Option[Date]       = task.dueDate
}
