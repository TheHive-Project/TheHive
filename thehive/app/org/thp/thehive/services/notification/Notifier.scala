package org.thp.thehive.services.notification

import java.util

import gremlin.scala.Graph
import org.thp.scalligraph.BadConfigurationError
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation, User}
import play.api.{ConfigLoader, Configuration}

import scala.collection.JavaConverters.mapAsJavaMap
import scala.util.{Failure, Success, Try}

trait Notifier {

  val name: String

  def asJavaMap(m: Map[String, Map[String, String]]): util.Map[String, util.Map[String, String]] = mapAsJavaMap(
    m.map(v => (v._1, mapAsJavaMap(v._2)))
  )

  def execute(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: User with Entity)(
      implicit graph: Graph
  ): Try[Unit]

  /**
    * Gets a map of Entities values
    * @param entities the data to summarize
    * @param nonContextualEntities the entity names not considered as a Context one
    * @return
    */
  protected def notificationMap(entities: List[Entity], nonContextualEntities: List[String]): Map[String, Map[String, String]] =
    entities
      .flatMap(getMap(_, nonContextualEntities))
      .toMap

  /**
    * Retrieves the data from an Entity db model (XXX with Entity) as a scala Map
    * @param cc the entity
    * @param nonContextualEntities the entity names not considered as a Context one
    * @return
    */
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

trait NotifierProvider extends (Configuration => Try[Notifier]) {
  implicit class RichConfig(configuration: Configuration) {

    def getOrFail[A: ConfigLoader](path: String): Try[A] =
      configuration
        .getOptional[A](path)
        .fold[Try[A]](Failure(BadConfigurationError(s"Configuration $path is missing")))(Success(_))
  }

  val name: String
}
