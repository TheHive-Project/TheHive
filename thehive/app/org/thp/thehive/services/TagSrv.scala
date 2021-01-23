package org.thp.thehive.services

import akka.actor.ActorRef
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.services.{IntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.thehive.models.{AlertTag, CaseTag, ObservableTag, Tag}
import org.thp.thehive.services.TagOps._

import javax.inject.{Inject, Named, Singleton}
import scala.util.{Success, Try}

@Singleton
class TagSrv @Inject() (appConfig: ApplicationConfig, @Named("integrity-check-actor") integrityCheckActor: ActorRef) extends VertexSrv[Tag] {

  private val autoCreateConfig: ConfigItem[Boolean, Boolean] =
    appConfig.item[Boolean]("tags.autocreate", "If true, create automatically tag if it doesn't exist")

  def autoCreate: Boolean = autoCreateConfig.get

  private val defaultNamespaceConfig: ConfigItem[String, String] =
    appConfig.item[String]("tags.defaultNamespace", "Default namespace of the automatically created tags")

  def defaultNamespace: String = defaultNamespaceConfig.get

  private val defaultColourConfig: ConfigItem[String, String] =
    appConfig.item[String]("tags.defaultColour", "Default colour of the automatically created tags")

  def defaultColour: String = defaultColourConfig.get

  def parseString(tagName: String): Tag =
    Tag.fromString(tagName, defaultNamespace, defaultColour)

  def getTag(tag: Tag)(implicit graph: Graph): Traversal.V[Tag] = startTraversal.getTag(tag)

  def getOrCreate(tagName: String)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] = {
    val tag = parseString(tagName)
    getTag(tag).getOrFail("Tag").recoverWith {
      case _ if autoCreate => create(tag)
    }
  }

  override def createEntity(e: Tag)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] = {
    integrityCheckActor ! EntityAdded("Tag")
    super.createEntity(e)
  }

  def create(tag: Tag)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] = createEntity(tag)

  override def exists(e: Tag)(implicit graph: Graph): Boolean = startTraversal.getByName(e.namespace, e.predicate, e.value).exists
}

object TagOps {

  implicit class TagOpsDefs(traversal: Traversal.V[Tag]) {

    def getTag(tag: Tag): Traversal.V[Tag] = getByName(tag.namespace, tag.predicate, tag.value)

    def getByName(namespace: String, predicate: String, value: Option[String]): Traversal.V[Tag] = {
      val t = traversal
        .has(_.namespace, namespace)
        .has(_.predicate, predicate)
      value.fold(t.hasNot(_.value))(v => t.has(_.value, v))
    }

    def displayName: Traversal[String, Vertex, Converter[String, Vertex]] = traversal.domainMap(_.toString)

    def fromCase: Traversal.V[Tag] = traversal.filter(_.in[CaseTag])

    def fromObservable: Traversal.V[Tag] = traversal.filter(_.in[ObservableTag])

    def fromAlert: Traversal.V[Tag] = traversal.filter(_.in[AlertTag])
  }

}

class TagIntegrityCheckOps @Inject() (val db: Database, val service: TagSrv) extends IntegrityCheckOps[Tag] {

  override def resolve(entities: Seq[Tag with Entity])(implicit graph: Graph): Try[Unit] = {
    firstCreatedEntity(entities).foreach {
      case (head, tail) =>
        tail.foreach(copyEdge(_, head))
        val tailIds = tail.map(_._id)
        logger.debug(s"Remove duplicated vertex: ${tailIds.mkString(",")}")
        service.getByIds(tailIds: _*).remove()
    }
    Success(())
  }
}
