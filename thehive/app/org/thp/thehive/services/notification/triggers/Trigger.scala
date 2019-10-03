package org.thp.thehive.services.notification.triggers

import scala.util.{Failure, Success, Try}

import play.api.{ConfigLoader, Configuration}

import gremlin.scala.Graph
import org.thp.scalligraph.BadConfigurationError
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation, User}

trait Trigger {
  val name: String

  def preFilter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity): Boolean

  def filter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: User with Entity)(
      implicit graph: Graph
  ): Boolean = !user.locked

  override def toString: String = s"Trigger($name)"
}

trait TriggerProvider extends (Configuration => Try[Trigger]) {
  val name: String
  implicit class RichConfig(configuration: Configuration) {

    def getOrFail[A: ConfigLoader](path: String): Try[A] =
      configuration
        .getOptional[A](path)
        .fold[Try[A]](Failure(BadConfigurationError(s"Configuration $path is missing")))(Success(_))
  }
}
