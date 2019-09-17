package org.thp.thehive.services.notification.email

import com.github.jknack.handlebars.Handlebars
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services.notification.{Notifier, NotifierProvider}
import play.api.Configuration
import play.api.libs.mailer.MailerClient

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

class Emailer(mailerClient: MailerClient, handlebars: Handlebars, notificationEmail: NotificationEmail) extends Notifier {
  override val name: String = "Emailer"

  override def execute(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: User with Entity)(
      implicit graph: Graph
  ): Try[Unit] =
    for {
      message <- getMessage(audit, context, organisation, user)
      _ <- Try(
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

  /**
    * Gets the formatted message string to be sent as user's notification
    * @param audit audit data
    * @param context optional context entity for additional data
    * @param organisation orga data
    * @param user user data
    * @return
    */
  def getMessage(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: User with Entity): Try[String] = {
    val nonContextualEntities = List("audit", "organisation", "user")
    for {
      model <- Try(
        context
          .fold(notificationMap(List(audit, organisation, user), nonContextualEntities))(
            c => notificationMap(List(c, audit, organisation, user), nonContextualEntities)
          )
      )
      message <- Try(handlebars.compileInline(notificationEmail.template).apply(model.asJavaMap))
    } yield message
  }
}
