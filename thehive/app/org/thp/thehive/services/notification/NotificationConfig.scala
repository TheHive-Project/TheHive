package org.thp.thehive.services.notification

import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads, Writes}

case class NotificationConfig(triggerConfig: Configuration, notifierConfig: Configuration, roleRestriction: Set[String])

object NotificationConfig {
  import org.thp.scalligraph.services.config.ConfigItemType._
  implicit val reads: Reads[NotificationConfig] =
    ((JsPath \ "trigger").read[Configuration] and
      (JsPath \ "notifier").read[Configuration] and
      (JsPath \ "hostRestriction").readWithDefault[Set[String]](Set.empty))(NotificationConfig.apply _)

  implicit val writes: Writes[NotificationConfig] = Writes[NotificationConfig] { config =>
    Json.obj("triggerConfig" -> config.triggerConfig, "notifierConfig" -> config.notifierConfig, "roleRestriction" -> config.roleRestriction)
  }

}
