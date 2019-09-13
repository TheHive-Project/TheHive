package org.thp.thehive.services.notification.email

import com.github.jknack.handlebars.Handlebars
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services.notification.email.templates.Context
import org.thp.thehive.services.notification.{Notifier, NotifierProvider}
import play.api.Configuration

import scala.collection.JavaConverters.mapAsJavaMap
import scala.util.Try

@Singleton
class EmailerProvider @Inject() extends NotifierProvider {
  override val name: String = "Emailer"

  override def apply(config: Configuration): Try[Notifier] =
    for {
      template <- config.getOrFail[String]("message")
    } yield new Emailer(new Handlebars(), template)
}

class Emailer(handlebars: Handlebars, template: String) extends Notifier {
  override val name: String = "Emailer"

  override def execute(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: User with Entity)(
      implicit graph: Graph
  ): Try[Unit] = {
    val model = notificationMap(audit, Context(context), organisation, user)
    Try(
      handlebars
        .compileInline(template)
        .apply(model)
    )
  }

  private def notificationMap(audit: Audit with Entity, context: Context, organisation: Organisation with Entity, user: User with Entity) =
    mapAsJavaMap(
      Map(
        "name"             -> user.name,
        "requestId"        -> audit.requestId,
        "action"           -> audit.action,
        "objectType"       -> audit.objectType.getOrElse(""),
        "objectId"         -> audit.objectId.getOrElse(""),
        "createdBy"        -> audit._createdBy,
        "createdAt"        -> audit._createdAt,
        "contextLabel"     -> context.label,
        "contextId"        -> context.id,
        "organisationName" -> organisation.name
      )
    )
}
