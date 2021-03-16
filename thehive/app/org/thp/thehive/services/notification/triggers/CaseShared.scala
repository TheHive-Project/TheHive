package org.thp.thehive.services.notification.triggers

import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation}
import play.api.Configuration
import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}
import scala.util.{Success, Try}

@Singleton
class CaseShareProvider @Inject() extends TriggerProvider {
  override val name: String                               = "CaseShared"
  override def apply(config: Configuration): Try[Trigger] = Success(new CaseShared())
}

class CaseShared() extends Trigger {
  override val name: String = "CaseShared"

  override def preFilter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity): Boolean =
    audit.action == Audit.update && audit
      .objectType
      .contains("Case") && audit.details.flatMap(d => Try(Json.parse(d)).toOption).exists(d => (d \ "share").isDefined)
}
