package org.thp.thehive.services.notification.triggers

import org.thp.thehive.models.Audit
import play.api.Configuration

import scala.util.{Success, Try}

class AlertCreatedProvider extends TriggerProvider {
  override val name: String                               = "AlertCreated"
  override def apply(config: Configuration): Try[Trigger] = Success(AlertCreated)
}

object AlertCreated extends GlobalTrigger {
  override val name: String        = "AlertCreated"
  override val auditAction: String = Audit.create
  override val entityName: String  = "Alert"
}
