package org.thp.thehive.services.notification.notifiers

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.helper.ConditionalHelpers
import org.thp.scalligraph.models.{Entity, Schema}
import org.thp.thehive.models.{Audit, User}

import java.util.{HashMap => JHashMap}
import scala.jdk.CollectionConverters._
import scala.util.Try

trait Template {
  val handlebars: Handlebars = new Handlebars().registerHelpers(classOf[ConditionalHelpers])
  val schema: Schema

  /**
    * Retrieves the data from an Entity db model (XXX with Entity) as a scala Map
    * @param cc the entity
    * @return
    */
  private def getMap(cc: Entity): Map[String, String] =
    schema
      .getModel(cc._label)
      .fold(Map.empty[String, String]) {
        _.fields
          .keys
          .filterNot(_ == "password")
          .flatMap { f =>
            cc.getClass.getSuperclass.getDeclaredMethod(f).invoke(cc) match {
              case option: Option[_] => option.map(f -> _.toString)
              case list: Seq[_]      => Some(f -> list.mkString("[", ",", "]"))
              case set: Set[_]       => Some(f -> set.mkString("[", ",", "]"))
              case other             => Some(f -> other.toString)
            }
          }
          .toMap
      } +
      ("_id"        -> cc._id.toString) +
      ("_type"      -> cc._label) +
      ("_createdAt" -> cc._createdAt.toString) +
      ("_createdBy" -> cc._createdBy) +
      ("_updatedAt" -> cc._updatedAt.fold("never")(_.toString)) +
      ("_updatedBy" -> cc._updatedBy.getOrElse("nobody"))

  def buildUrl(baseUrl: String, `object`: Option[Entity], context: Option[Entity]): Option[String] =
    `object`.flatMap { obj =>
      obj._label match {
        case "Case"             => Some(s"$baseUrl/index.html#/case/${obj._id}")
        case "Task"             => context.map(ctx => s"$baseUrl/index.html#/case/${ctx._id}/tasks/${obj._id}")
        case "Log"              => context.map(ctx => s"$baseUrl/index.html#/case/${ctx._id}")
        case "CaseTemplate"     => None
        case "Alert"            => Some(s"$baseUrl/index.html#/alert/list")
        case "Observable"       => context.map(ctx => s"$baseUrl/index.html#/case/${ctx._id}/observables/${obj._id}")
        case "User"             => Some(s"$baseUrl/index.html#/administration/users")
        case "Dashboard"        => Some(s"$baseUrl/index.html#/dashboards/${obj._id}")
        case "Organisation"     => None
        case "Job"              => None
        case "Action"           => buildUrl(baseUrl, context, None)
        case "AnalyzerTemplate" => None
      }
    }

  def buildMessage(
      template: String,
      audit: Audit with Entity,
      context: Option[Entity],
      `object`: Option[Entity],
      user: Option[User with Entity],
      baseUrl: String
  ): Try[String] = {
    val model = new JHashMap[String, AnyRef]
    model.put("audit", getMap(audit).asJava)
    user.foreach(u => model.put("user", getMap(u).asJava))
    context.foreach(c => model.put("context", getMap(c).asJava))
    `object`.foreach(o => model.put("object", getMap(o).asJava))
    buildUrl(baseUrl, `object`, context).foreach(url => model.put("url", url))
    Try(handlebars.compileInline(template).apply(model))
  }
}
