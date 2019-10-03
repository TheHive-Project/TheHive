package org.thp.thehive.services.notification.triggers

import scala.util.{Success, Try}

import play.api.Configuration

import javax.inject.{Inject, Singleton}
import org.thp.thehive.models.Audit

@Singleton
class AlertCreatedProvider @Inject() extends TriggerProvider {
  override val name: String                               = "AlertCreated"
  override def apply(config: Configuration): Try[Trigger] = Success(new AlertCreated())
}

class AlertCreated extends GlobalTrigger {
  override val name: String        = "AlertCreated"
  override val auditAction: String = Audit.create
  override val entityName: String  = "Alert"
}
