package org.thp.thehive.services.notification.triggers

import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation, User}

trait GlobalTrigger extends Trigger {
  val entityName: String
  val auditAction: String

  override def preFilter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity): Boolean =
    audit.action == auditAction && audit.objectType.contains(entityName)

  override def filter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: Option[User with Entity])(
      implicit graph: Graph
  ): Boolean =
    preFilter(audit, context, organisation) &&
      super.filter(audit, context, organisation, user) &&
      user.fold(true)(_.login != audit._createdBy)
}
