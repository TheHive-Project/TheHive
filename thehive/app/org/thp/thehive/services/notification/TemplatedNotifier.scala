package org.thp.thehive.services.notification

import java.util

import com.github.jknack.handlebars.Handlebars
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Audit, Organisation, User}

import scala.collection.JavaConverters.mapAsJavaMap
import scala.language.postfixOps
import scala.util.Try

abstract class TemplatedNotifier(handlebars: Handlebars) extends Notifier {
  implicit class MapConverter(m: Map[String, Map[String, String]]) {

    def asJavaMap: util.Map[String, util.Map[String, String]] = mapAsJavaMap(
      m.map(v => (v._1, mapAsJavaMap(v._2)))
    )
  }

  /**
    * List of Entity names that are used directly
    * in handlebars template (i.e. audit.xxx, user.xxx as opposed to context.xxx)
    */
  protected val nonContextualEntities: List[String]

  /**
    * Gets the formatted message string to be sent as user's notification
    * @param audit audit data
    * @param context optional context entity for additional data
    * @param organisation orga data
    * @param user user data
    * @return
    */
  def message(
      audit: Audit with Entity,
      context: Option[Entity],
      organisation: Organisation with Entity,
      user: User with Entity,
      template: String
  ): Try[String] =
    for {
      model <- Try(
        context
          .fold(notificationMap(List(audit, organisation, user)))(
            c => notificationMap(List(c, audit, organisation, user))
          )
      )
      message <- Try(handlebars.compileInline(template).apply(model.asJavaMap))
    } yield message

  /**
    * Gets a map of Entities values
    *
    * @param entities the data to summarize
    * @return
    */
  protected def notificationMap(entities: List[Entity]): Map[String, Map[String, String]] =
    entities
      .flatMap(getMap)
      .toMap

  /**
    * Retrieves the data from an Entity db model (XXX with Entity) as a scala Map
    *
    * @param cc the entity
    * @return
    */
  private def getMap(cc: Entity): Map[String, Map[String, String]] = {
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
