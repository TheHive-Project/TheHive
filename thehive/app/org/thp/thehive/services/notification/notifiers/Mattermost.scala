package org.thp.thehive.services.notification.notifiers

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

import play.api.libs.json.{Json, Reads, Writes}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.models.{Audit, Organisation, User}

case class MattermostNotification(text: String, url: String, channel: Option[String], username: Option[String])

object MattermostNotification {
  implicit val writes: Writes[MattermostNotification] = Json.writes[MattermostNotification]
}

@Singleton
class MattermostProvider @Inject()(appConfig: ApplicationConfig, ws: WSClient, ec: ExecutionContext) extends NotifierProvider {
  override val name: String                            = "Mattermost"
  implicit val optionStringRead: Reads[Option[String]] = Reads.optionNoError[String]

  val webhookConfig: ConfigItem[String, String] =
    appConfig.item[String]("notification.mattermost.webhook", "Webhook url declared on Mattermost side")

  val usernameConfig: ConfigItem[Option[String], Option[String]] =
    appConfig.item[Option[String]]("notification.mattermost.username", "Username who send Mattermost message")

  val templateConfig: ConfigItem[String, String] =
    appConfig.item[String]("notification.mattermost.template", "Template message to send")

  val baseUrlConfig: ConfigItem[String, String] =
    appConfig.item[String]("application.baseUrl", "Application base URL")

  override def apply(config: Configuration): Try[Notifier] = {
    val template         = config.getOptional[String]("message").getOrElse(templateConfig.get)
    val channel          = config.getOptional[String]("channel")
    val usernameOverride = usernameConfig.get
    val webhook          = webhookConfig.get
    val mattermost       = new Mattermost(ws, MattermostNotification(template, webhook, channel, usernameOverride), baseUrlConfig.get, ec)
    Success(mattermost)
  }
}

class Mattermost(ws: WSClient, mattermostNotification: MattermostNotification, baseUrl: String, implicit val ec: ExecutionContext)
    extends Notifier
    with Template {
  lazy val logger           = Logger(getClass)
  override val name: String = "Mattermost"

  def execute(
      audit: Audit with Entity,
      context: Option[Entity],
      `object`: Option[Entity],
      organisation: Organisation with Entity,
      user: User with Entity
  )(
      implicit graph: Graph
  ): Future[Unit] =
    for {
      finalMessage <- Future.fromTry(buildMessage(mattermostNotification.text, audit, context, `object`, user, baseUrl))
      _ <- ws
        .url(mattermostNotification.url)
        .post(Json.toJson(mattermostNotification.copy(text = finalMessage)))
    } yield ()
}
