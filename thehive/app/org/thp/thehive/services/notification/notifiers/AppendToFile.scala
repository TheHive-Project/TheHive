package org.thp.thehive.services.notification.notifiers

import java.nio.charset.Charset
import java.nio.file.{Files, Paths, StandardOpenOption}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

import play.api.Configuration

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.models.{Audit, Organisation, User}

@Singleton
class AppendToFileProvider @Inject()(appConfig: ApplicationConfig) extends NotifierProvider {
  override val name: String = "AppendToFile"

  val templateConfig: ConfigItem[String, String] =
    appConfig.item[String]("notification.appendToFile.template", "Template text to append")

  val baseUrlConfig: ConfigItem[String, String] =
    appConfig.item[String]("application.baseUrl", "Application base URL")

  override def apply(config: Configuration): Try[Notifier] =
    config.getOrFail[String]("file").map { filename =>
      val template = config.getOptional[String]("message").getOrElse(templateConfig.get)
      val charset  = config.getOptional[String]("charset").fold(Charset.defaultCharset())(Charset.forName)
      new AppendToFile(filename, template, charset, baseUrlConfig.get)
    }
}

class AppendToFile(filename: String, template: String, charset: Charset, baseUrl: String) extends Notifier with Template {
  override val name: String = "AppendToFile"

  override def execute(
      audit: Audit with Entity,
      context: Option[Entity],
      `object`: Option[Entity],
      organisation: Organisation with Entity,
      user: User with Entity
  )(
      implicit graph: Graph
  ): Future[Unit] =
    buildMessage(template, audit, context, `object`, user, baseUrl).fold(
      Future.failed[Unit],
      message => Future(Files.write(Paths.get(filename), message.getBytes(charset), StandardOpenOption.APPEND, StandardOpenOption.CREATE))
    )
}
