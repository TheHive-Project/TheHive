package org.thp.thehive.services.notification

import scala.util.{Failure, Success, Try}

import play.api.{ConfigLoader, Configuration}

import gremlin.scala.Graph
import org.thp.scalligraph.BadConfigurationError
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation, User}

trait Notifier {
  val name: String

  def execute(audit: Audit with Entity, context: Entity, organisation: Organisation with Entity, user: User with Entity)(
      implicit graph: Graph
  ): Try[Unit]
}

trait NotifierProvider extends (Configuration => Try[Notifier]) {
  val name: String
  implicit class RichConfig(configuration: Configuration) {

    def getOrFail[A: ConfigLoader](path: String): Try[A] =
      configuration
        .getOptional[A](path)
        .fold[Try[A]](Failure(BadConfigurationError(s"Configuration $path is missing")))(Success(_))
  }
}
