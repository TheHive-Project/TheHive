package org.thp.thehive.services.notification.triggers

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation, User}
import play.api.Configuration

import scala.util.{Success, Try}

@Singleton
class AlertCreatedProvider @Inject() extends TriggerProvider {
  override val name: String                               = "AlertCreated"
  override def apply(config: Configuration): Try[Trigger] = Success(new AlertCreated())
}

class AlertCreated extends Trigger {
  override val name: String = "AlertCreated"

  override def preFilter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity): Boolean =
    audit.action == Audit.create && audit.objectType.contains("Alert")

  override def filter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: User with Entity)(
      implicit graph: Graph
  ): Boolean =
    preFilter(audit, context, organisation) &&
      super.filter(audit, context, organisation, user) &&
      user.login != audit._createdBy
}
