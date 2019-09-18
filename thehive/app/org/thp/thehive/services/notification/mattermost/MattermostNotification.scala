package org.thp.thehive.services.notification.mattermost

import play.api.libs.json.{Format, Json}

case class MattermostNotification(text: String, url: String, channel: Option[String], username: Option[String])

object MattermostNotification {
  implicit val format: Format[MattermostNotification] = Json.format[MattermostNotification]
}
