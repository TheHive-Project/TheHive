package org.thp.thehive.services.notification.triggers

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services.TaskSrv
import play.api.Configuration

import scala.util.{Success, Try}

@Singleton
class TaskAssignedProvider @Inject() (taskSrv: TaskSrv) extends TriggerProvider {
  override val name: String                               = "TaskAssigned"
  override def apply(config: Configuration): Try[Trigger] = Success(new TaskAssigned(taskSrv))
}

class TaskAssigned(taskSrv: TaskSrv) extends Trigger {
  override val name: String = "TaskAssigned"

  override def preFilter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity): Boolean =
    audit.action == Audit.update && audit.objectType.contains("Task")

  override def filter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: Option[User with Entity])(
      implicit graph: Graph
  ): Boolean = user.fold(false) { u =>
    preFilter(audit, context, organisation) &&
    super.filter(audit, context, organisation, user) &&
    u.login != audit._createdBy &&
    audit.objectId.fold(false)(taskAssignee(_, u.login).isDefined)
  }

  def taskAssignee(taskId: String, login: String)(implicit graph: Graph): Option[User with Entity] =
    taskSrv.getByIds(taskId).assignee.has("login", login).headOption()
}
