package org.thp.thehive.services.notification.notifiers

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.models.{Audit, Organisation, User}
import play.api.Configuration
import play.api.libs.mailer.{Email, MailerClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Try}

@Singleton
class EmailerProvider @Inject()(appConfig: ApplicationConfig, mailerClient: MailerClient) extends NotifierProvider {
  override val name: String = "Emailer"

  val subjectConfig: ConfigItem[String, String] =
    appConfig.item[String]("notification.emailer.subject", "Subject of mail")

  val fromConfig: ConfigItem[String, String] =
    appConfig.item[String]("notification.emailer.from", "Mail sender address")

  val templateConfig: ConfigItem[String, String] =
    appConfig.item[String]("notification.emailer.template", "Template message to send")

  val baseUrlConfig: ConfigItem[String, String] =
    appConfig.item[String]("application.baseUrl", "Application base URL")

  override def apply(config: Configuration): Try[Notifier] = {
    val template = config.getOptional[String]("message").getOrElse(templateConfig.get)
    val emailer  = new Emailer(mailerClient, subjectConfig.get, fromConfig.get, template, baseUrlConfig.get)
    Success(emailer)
  }
}

class Emailer(mailerClient: MailerClient, subject: String, from: String, template: String, baseUrl: String) extends Notifier with Template {
  override val name: String = "Emailer"

  override def execute(
      audit: Audit with Entity,
      context: Option[Entity],
      `object`: Option[Entity],
      organisation: Organisation with Entity,
      user: User with Entity
  )(
      implicit graph: Graph
  ): Future[Unit] =
    buildMessage(template, audit, context, `object`, user, baseUrl)
      .fold(
        Future.failed[Unit],
        message => Future(mailerClient.send(Email(subject = subject, from = from, to = Seq(user.login), bodyText = Some(message)))).map(_ => ())
      )
}
