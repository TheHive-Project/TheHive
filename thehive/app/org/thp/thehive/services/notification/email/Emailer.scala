package org.thp.thehive.services.notification.email

import com.github.jknack.handlebars.Handlebars
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services.notification.{Notifier, NotifierProvider, TemplatedNotifier}
import play.api.Configuration
import play.api.libs.mailer.MailerClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

@Singleton
class EmailerProvider @Inject()(mailerClient: MailerClient) extends NotifierProvider {
  override val name: String = "Emailer"

  override def apply(config: Configuration): Try[Notifier] =
    for {
      template <- config.getOrFail[String]("message")
      subj = config.getOptional[String]("subject")
      from = config.getOptional[String]("from")
    } yield new Emailer(mailerClient, new Handlebars(), NotificationEmail(subj, from, template))
}

class Emailer(mailerClient: MailerClient, handlebars: Handlebars, notificationEmail: NotificationEmail) extends TemplatedNotifier(handlebars) {
  override val name: String                                  = "Emailer"
  override protected val nonContextualEntities: List[String] = List("audit", "organisation", "user")

  override def execute(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: User with Entity)(
      implicit graph: Graph
  ): Future[Unit] =
    for {
      message <- Future.fromTry(message(audit, context, organisation, user, notificationEmail.template))
      _ <- Future(
        mailerClient.send(
          notificationEmail
            .toEmail
            .copy(
              bodyText = Some(message),
              to = Seq(user.login)
            )
        )
      )
    } yield ()
}
