package org.thp.thehive.services.notification.mattermost

import com.github.jknack.handlebars.Handlebars
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services.notification.{Notifier, NotifierProvider, TemplatedNotifier}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

@Singleton
class MattermostProvider @Inject()(appConfig: ApplicationConfig, ws: WSClient) extends NotifierProvider {
  override val name: String = "Mattermost"

  val notificationWebhookConf: ConfigItem[String, String] =
    appConfig.item[String]("mattermost.notificationWebhook", "Incoming webhook url declared on Mattermost side")

  override def apply(config: Configuration): Try[Notifier] =
    for {
      template <- config.getOrFail[String]("message")
      channel          = config.getOptional[String]("channel")
      usernameOverride = config.getOptional[String]("username")
      webhook <- Try(notificationWebhookConf.get)
    } yield new Mattermost(new Handlebars(), ws, MattermostNotification(template, webhook, channel, usernameOverride))
}

class Mattermost(handlebars: Handlebars, ws: WSClient, mattermostNotification: MattermostNotification) extends TemplatedNotifier(handlebars) {
  lazy val logger                                            = Logger(getClass)
  override val name: String                                  = "Mattermost"
  override protected val nonContextualEntities: List[String] = List("audit", "organisation", "user")

  def execute(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: User with Entity)(
      implicit graph: Graph
  ): Future[Unit] = {
    for {
      finalMessage <- Future.fromTry(message(audit, context, organisation, user, mattermostNotification.text))
      _ <- ws
        .url(mattermostNotification.url)
        .withHttpHeaders("Content-Type" -> "application/json")
        .post(Json.toJson(mattermostNotification.copy(text = finalMessage)))
    } yield ()
  } recover {
    case e => logger.error(s"Mattermost request failed: ${e.getMessage}")
  }
}
