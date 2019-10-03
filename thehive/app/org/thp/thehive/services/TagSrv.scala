package org.thp.thehive.services

import scala.util.Try

import gremlin.scala.{Graph, GremlinScala, Key, P, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.VertexSrv
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.thehive.models.Tag

@Singleton
class TagSrv @Inject()(appConfig: ApplicationConfig)(implicit db: Database) extends VertexSrv[Tag, TagSteps] {

  val autoCreateConfig: ConfigItem[Boolean, Boolean] =
    appConfig.item[Boolean]("tags.autocreate", "If true, create automatically tag if it doesn't exist")

  def autoCreate: Boolean = autoCreateConfig.get

  val defaultNamespaceConfig: ConfigItem[String, String] =
    appConfig.item[String]("tags.defaultNamespace", "Default namespace of the automatically created tags")

  def defaultNamespace: String = defaultNamespaceConfig.get

  val defaultColourConfig: ConfigItem[String, Int] =
    appConfig.mapItem[String, Int](
      "tags.defaultColour",
      "Default colour of the automatically created tags", {
        case s if s(0) == '#' => Try(Integer.parseUnsignedInt(s.tail, 16)).getOrElse(defaultColour)
        case _                => defaultColour
      }
    )
  def defaultColour: Int = defaultColourConfig.get

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): TagSteps = new TagSteps(raw)

  def parseString(tagName: String): Tag =
    Tag.fromString(tagName, defaultNamespace, defaultColour)

  override def get(idOrName: String)(implicit graph: Graph): TagSteps =
    getByIds(idOrName)

  def get(tag: Tag)(implicit graph: Graph): TagSteps = initSteps.get(tag)

  def getOrCreate(tagName: String)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] = {
    val tag = parseString(tagName)
    get(tag).getOrFail().recoverWith {
      case _ if autoCreate => create(tag)
    }
  }

  def create(tag: Tag)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] = createEntity(tag)
}

class TagSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Tag](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): TagSteps = new TagSteps(newRaw)
  override def newInstance(): TagSteps                             = new TagSteps(raw.clone())

  def get(tag: Tag): TagSteps = getByName(tag.namespace, tag.predicate, tag.value)

  def getByName(namespace: String, predicate: String, value: Option[String]): TagSteps = {
    val step = newInstance(
      raw
        .has(Key("namespace") of namespace)
        .has(Key("predicate") of predicate)
    )
    value.fold(step.hasNot(Key("value")))(v => step.has(Key("value"), P.eq(v)))
  }

  def displayName: Traversal[String, String] = this.map(_.toString)
}
