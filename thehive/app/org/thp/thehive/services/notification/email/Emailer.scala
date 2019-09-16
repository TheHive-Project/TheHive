package org.thp.thehive.services.notification.email

import com.github.jknack.handlebars.Handlebars
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services.notification.{Notifier, NotifierProvider}
import play.api.Configuration
import play.api.libs.mailer.MailerClient

import scala.collection.JavaConverters.mapAsJavaMap
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
  ): Try[Unit] = {
    val nonContextualEntities = List("audit", "organisation", "user")
    for {
      model <- Try(
        context
          .fold(notificationMap(List(audit, organisation, user), nonContextualEntities))(
            c => notificationMap(List(c, audit, organisation, user), nonContextualEntities)
          )
      )
      _ <- Try(
        mailerClient.send(
          notificationEmail
            .toEmail
            .copy(
              bodyText = Some(
                handlebars
                  .compileInline(notificationEmail.template)
                  .apply(model)
              ),
              to = Seq(user.login)
            )
        )
      )
    } yield ()
  }

  private def notificationMap(entities: List[Entity], nonContextualEntities: List[String]) =
    mapAsJavaMap(
      entities
        .flatMap(getMap(_, nonContextualEntities))
        .map(e => (e._1, mapAsJavaMap(e._2)))
        .toMap
    )

  private def getMap(cc: Entity, nonContextualEntities: List[String]) = {
    val baseFields = {
      for {
        field <- cc.getClass.getDeclaredFields
        _     = field.setAccessible(true)
        name  = field.getName
        value = field.get(cc)
      } yield name -> value.toString
    } toMap

    val fields = cc
      ._model
      .fields
      .keys
      .map { f =>
        f -> cc.getClass.getSuperclass.getDeclaredMethod(f).invoke(cc).toString
    } toMap
    val l     = cc._model.label.toLowerCase
    val label = if (nonContextualEntities.contains(l)) l else "context"

    Map(label -> baseFields.++(fields))
  }
}
