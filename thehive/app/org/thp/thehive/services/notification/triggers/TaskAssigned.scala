package org.thp.thehive.services.notification.triggers

import scala.util.{Success, Try}

import play.api.Configuration

import gremlin.scala.{Graph, Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services.TaskSrv

@Singleton
class TaskAssignedProvider @Inject()(taskSrv: TaskSrv) extends TriggerProvider {
  override val name: String                               = "TaskAssigned"
  override def apply(config: Configuration): Try[Trigger] = Success(new TaskAssigned(taskSrv))
}

class TaskAssigned(taskSrv: TaskSrv) extends Trigger {
  override val name: String = "TaskAssigned"

  override def preFilter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity): Boolean =
    audit.action == Audit.update && audit.objectType.contains("Task")

  override def filter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: User with Entity)(
      implicit graph: Graph
  ): Boolean =
    preFilter(audit, context, organisation) &&
      super.filter(audit, context, organisation, user) &&
      user.login != audit._createdBy &&
      audit.objectId.fold(false)(taskAssignee(_, user.login).isDefined)

  def taskAssignee(taskId: String, login: String)(implicit graph: Graph): Option[User with Entity] =
    taskSrv.getByIds(taskId).user.has(Key("login"), P.eq(login)).headOption()
}
