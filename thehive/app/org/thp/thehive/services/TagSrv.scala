package org.thp.thehive.services

import akka.actor.ActorRef
import org.apache.tinkerpop.gremlin.structure.{Graph, Vertex}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.services.{IntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.scalligraph.utils.FunctionalCondition.When
import org.thp.thehive.models._
import org.thp.thehive.services.TagOps._

import javax.inject.{Inject, Named, Singleton}
import scala.util.matching.Regex
import scala.util.{Success, Try}

@Singleton
class TagSrv @Inject() (
    appConfig: ApplicationConfig,
    @Named("integrity-check-actor") integrityCheckActor: ActorRef,
    organisationSrv: OrganisationSrv
)(implicit
    @Named("with-thehive-schema") db: Database
) extends VertexSrv[Tag] {

  private val autoCreateConfig: ConfigItem[Boolean, Boolean] =
    appConfig.item[Boolean]("tags.autocreate", "If true, create automatically tag if it doesn't exist")

  private val defaultNamespaceConfig: ConfigItem[String, String] =
    appConfig.item[String]("tags.defaultNamespace", "Default namespace of the automatically created tags")

  private val defaultColourConfig: ConfigItem[String, String] =
    appConfig.item[String]("tags.defaultColour", "Default colour of the automatically created tags")

  def autoCreate: Boolean      = autoCreateConfig.get
  def defaultNamespace: String = defaultNamespaceConfig.get
  def defaultColour: String    = defaultColourConfig.get

  private def freeTag(tagName: String)(implicit graph: Graph, authContext: AuthContext): Tag =
    Tag(freeTagNamespace, tagName, None, None, defaultColour)

  private def freeTagNamespace(implicit graph: Graph, authContext: AuthContext): String =
    s"_freetags_${organisationSrv.currentId(graph, authContext).value}"

  def fromString(tagName: String): Option[(String, String, Option[String])] = {
    val namespacePredicateValue: Regex = "([^\".:=]+)[.:]([^\".=]+)=\"?([^\"]+)\"?".r
    val namespacePredicate: Regex      = "([^\".:=]+)[.:]([^\".=]+)".r

    tagName match {
      case namespacePredicateValue(namespace, predicate, value) if value.exists(_ != '=') =>
        Some((namespace.trim, predicate.trim, Some(value.trim)))
      case namespacePredicate(namespace, predicate) =>
        Some((namespace.trim, predicate.trim, None))
      case _ => None
    }
  }

  def getTag(tag: Tag)(implicit graph: Graph): Traversal.V[Tag] = startTraversal.getTag(tag)

  def getOrCreate(tagName: String)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] =
    fromString(tagName) match {
      case Some((ns, pred, v)) =>
        startTraversal
          .getByName(ns, pred, v)
          .getOrFail("Tag")
          .orElse(
            startTraversal
              .getByName(freeTagNamespace, ns + pred + v.getOrElse(""), None)
              .getOrFail("Tag")
              .orElse(create(freeTag(tagName)))
          )
      case None =>
        startTraversal
          .getByName(freeTagNamespace, tagName, None)
          .getOrFail("Tag")
          .orElse(create(freeTag(tagName)))
    }

  def create(tag: Tag)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] = {
    integrityCheckActor ! EntityAdded("Tag")
    super.createEntity(tag)
  }

  override def exists(e: Tag)(implicit graph: Graph): Boolean = startTraversal.getByName(e.namespace, e.predicate, e.value).exists

  def update(
      tag: Tag with Entity,
      input: Tag
  )(implicit graph: Graph): Try[Tag with Entity] =
    for {
      updatedTag <- get(tag)
        .when(tag.description != input.description)(_.update(_.description, input.description))
        .when(tag.colour != input.colour)(_.update(_.colour, input.colour))
        .getOrFail("Tag")
    } yield updatedTag
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

class TagIntegrityCheckOps @Inject() (@Named("with-thehive-schema") val db: Database, val service: TagSrv) extends IntegrityCheckOps[Tag] {

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
