package org.thp.thehive.services.notification.triggers

import scala.util.{Success, Try}

import play.api.Configuration

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services.LogSrv

@Singleton
class LogInMyTaskProvider @Inject()(logSrv: LogSrv) extends TriggerProvider {
  override val name: String                               = "LogInMyTask"
  override def apply(config: Configuration): Try[Trigger] = Success(new LogInMyTask(logSrv))
}

class LogInMyTask(logSrv: LogSrv) extends Trigger {
  override val name: String = "LogInMyTask"

  override def preFilter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity): Boolean =
    audit.action == Audit.create && audit.objectType.contains("Log")

  override def filter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: User with Entity)(
      implicit graph: Graph
  ): Boolean =
    super.filter(audit, context, organisation, user) &&
      preFilter(audit, context, organisation) &&
      user.login != audit._createdBy &&
      audit.objectId.fold(false)(taskAssignee(_).fold(false)(_ == user.login))

  def taskAssignee(logId: String)(implicit graph: Graph): Option[String] = logSrv.getByIds(logId).task.user.login.headOption()
}
