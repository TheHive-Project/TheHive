package org.thp.thehive.services.notification.notifiers

import org.thp.scalligraph.BadConfigurationError
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.traversal.Graph
import org.thp.thehive.models.{Audit, Organisation, User}
import play.api.{ConfigLoader, Configuration}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait Notifier {

  val name: String

  def execute(
      audit: Audit with Entity,
      context: Option[Map[String, Seq[Any]] with Entity],
      `object`: Option[Map[String, Seq[Any]] with Entity],
      organisation: Organisation with Entity,
      user: Option[User with Entity]
  )(implicit
      graph: Graph
  ): Future[Unit]

}

trait NotifierProvider extends (Configuration => Try[Notifier]) {
  implicit class RichConfig(configuration: Configuration) {

    def getOrFail[A: ConfigLoader](path: String): Try[A] =
      configuration
        .getOptional[A](path)
        .fold[Try[A]](Failure(BadConfigurationError(s"Configuration $path is missing")))(Success(_))
  }

  val name: String
}
