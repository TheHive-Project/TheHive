package org.thp.thehive.services.notification.triggers

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.traversal.Graph
import org.thp.thehive.models.{Audit, Organisation, User}
import play.api.Configuration

import scala.util.{Success, Try}

class CaseCreatedProvider extends TriggerProvider {
  override val name: String                               = "CaseCreated"
  override def apply(config: Configuration): Try[Trigger] = Success(CaseCreated)
}

object CaseCreated extends Trigger {
  override val name: String = "CaseCreated"

  override def preFilter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity): Boolean =
    audit.action == Audit.create && audit.objectType.contains("Case")

  override def filter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: Option[User with Entity])(
      implicit graph: Graph
  ): Boolean =
    super.filter(audit, context, organisation, user) &&
      preFilter(audit, context, organisation) &&
      user.fold(true)(_.login != audit._createdBy)
}
