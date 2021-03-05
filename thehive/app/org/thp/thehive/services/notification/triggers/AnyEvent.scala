package org.thp.thehive.services.notification.triggers

import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation}
import play.api.Configuration

import javax.inject.Singleton
import scala.util.{Success, Try}

@Singleton
class AnyEventProvider extends TriggerProvider {
  override val name: String = "AnyEvent"

  override def apply(config: Configuration): Try[Trigger] = Success(AnyEvent)
}

object AnyEvent extends Trigger {
  override val name: String = "AnyEvent"

  override def preFilter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity): Boolean = true
}
