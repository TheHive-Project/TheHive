package org.thp.thehive.services.notification.triggers

import org.thp.scalligraph.EntityId
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.traversal.Graph
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services.{TaskSrv, TheHiveOpsNoDeps}
import play.api.Configuration

import scala.util.{Success, Try}

class TaskAssignedProvider(taskSrv: TaskSrv) extends TriggerProvider {
  override val name: String                               = "TaskAssigned"
  override def apply(config: Configuration): Try[Trigger] = Success(new TaskAssigned(taskSrv))
}

class TaskAssigned(taskSrv: TaskSrv) extends Trigger with TheHiveOpsNoDeps {
  override val name: String = "TaskAssigned"

  override def preFilter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity): Boolean =
    audit.action == Audit.update && audit.objectType.contains("Task")

  override def filter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: Option[User with Entity])(
      implicit graph: Graph
  ): Boolean =
    user.fold(false) { u =>
      preFilter(audit, context, organisation) &&
      super.filter(audit, context, organisation, user) &&
      u.login != audit._createdBy &&
      audit.objectEntityId.fold(false)(taskAssignee(_, u._id).isDefined)
    }

  def taskAssignee(taskId: EntityId, userId: EntityId)(implicit graph: Graph): Option[User with Entity] =
    taskSrv.getByIds(taskId).assignee.get(userId).headOption

  override def toString: String = "TaskAssigned"
}
