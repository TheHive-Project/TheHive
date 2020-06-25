package org.thp.thehive.services

import akka.actor.ActorRef
import gremlin.scala.{Graph, GremlinScala, Key, Vertex}
import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.services.{IntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.thehive.models.Tag

import scala.util.{Success, Try}

@Singleton
class TagSrv @Inject() (appConfig: ApplicationConfig, @Named("integrity-check-actor") integrityCheckActor: ActorRef)(
    implicit @Named("with-thehive-schema") db: Database
) extends VertexSrv[Tag, TagSteps] {

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
    get(tag).getOrFail("Tag").recoverWith {
      case _ if autoCreate => create(tag)
    }
  }

  override def createEntity(e: Tag)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] = {
    integrityCheckActor ! IntegrityCheckActor.EntityAdded("Tag")
    super.createEntity(e)
  }

  def create(tag: Tag)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] = createEntity(tag)

  override def exists(e: Tag)(implicit graph: Graph): Boolean = initSteps.getByName(e.namespace, e.predicate, e.value).exists()
}

class TagSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph) extends VertexSteps[Tag](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): TagSteps = new TagSteps(newRaw)
  override def newInstance(): TagSteps                             = new TagSteps(raw.clone())

  def get(tag: Tag): TagSteps = getByName(tag.namespace, tag.predicate, tag.value)

  def getByName(namespace: String, predicate: String, value: Option[String]): TagSteps = {
    val step = newInstance(
      raw
        .has(Key("namespace") of namespace)
        .has(Key("predicate") of predicate)
    )
    value.fold(step.hasNot("value"))(v => step.has("value", v))
  }

  def displayName: Traversal[String, String] = this.map(_.toString)
}

class TagIntegrityCheckOps @Inject() (@Named("with-thehive-schema") val db: Database, val service: TagSrv) extends IntegrityCheckOps[Tag] {
  override def resolve(entities: List[Tag with Entity])(implicit graph: Graph): Try[Unit] = entities match {
    case head :: tail =>
      tail.foreach(copyEdge(_, head))
      tail.foreach(service.get(_).remove())
      Success(())
    case _ => Success(())
  }
}
