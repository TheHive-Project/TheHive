package org.thp.thehive.services.notification
import java.nio.charset.Charset
import java.nio.file.{Files, Paths, StandardOpenOption}

import scala.util.Try

import play.api.Configuration

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation, User}

@Singleton
class AppendToFileProvider @Inject() extends NotifierProvider {
  override val name: String = "AppendToFile"

  override def apply(config: Configuration): Try[Notifier] =
    for {
      filename <- config.getOrFail[String]("file")
      charset = config.getOptional[String]("charset").fold(Charset.defaultCharset())(Charset.forName)
    } yield new AppendToFile(filename, charset)
}

class AppendToFile(filename: String, charset: Charset) extends Notifier {
  override val name: String = "AppendToFile"

  override def execute(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: User with Entity)(
      implicit graph: Graph
  ): Try[Unit] = {
    val message =
      s"""
         |Audit (${audit.requestId}):
         |  ${audit.action} ${audit.objectType} ${audit.objectId}
         |  by ${audit._createdBy} at ${audit._createdAt}
         |Context ${context.map(_._model.label)} ${context.map(_._id)} (${organisation.name})
         |For user ${user.login}
         |----------------
         |""".stripMargin
    Try {
      Files.write(Paths.get(filename), message.getBytes(charset), StandardOpenOption.APPEND, StandardOpenOption.CREATE)
      ()
    }
  }
}
