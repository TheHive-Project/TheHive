package org.thp.thehive.services.notification.notifiers

import org.thp.scalligraph.models.{Entity, Schema}
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.Graph
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services.MailerProvider
import play.api.libs.json.Reads
import play.api.libs.mailer.{Email, MailerClient}
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class EmailerProvider(appConfig: ApplicationConfig, schema: Schema, ec: ExecutionContext) extends NotifierProvider {
  override val name: String = "Emailer"

  implicit def optionReads[A: Reads]: Reads[Option[A]] = Reads.optionNoError[A]

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
    val emailer  = new Emailer(MailerProvider(appConfig), subjectConfig.get, fromConfig.get, template, baseUrlConfig.get, schema, ec)
    Success(emailer)
  }
}

class Emailer(
    mailerClient: MailerClient,
    subject: String,
    from: String,
    template: String,
    baseUrl: String,
    val schema: Schema,
    implicit val ec: ExecutionContext
) extends Notifier
    with Template {
  lazy val logger: Logger   = Logger(getClass)
  override val name: String = "Emailer"

  override def execute(
      audit: Audit with Entity,
      context: Option[Map[String, Seq[Any]] with Entity],
      `object`: Option[Map[String, Seq[Any]] with Entity],
      organisation: Organisation with Entity,
      user: Option[User with Entity]
  )(implicit
      graph: Graph
  ): Future[Unit] =
    user.fold(Future.successful(logger.warn(s"Email can't be sent to an organisation"))) { u =>
      buildMessage(template, audit, context, `object`, user, baseUrl)
        .fold(
          Future.failed[Unit],
          message => Future(mailerClient.send(Email(subject = subject, from = from, to = Seq(u.login), bodyText = Some(message)))).map(_ => ())
        )
    }
}
