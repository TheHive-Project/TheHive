package org.thp.thehive.services

import akka.actor.ActorRef
import org.apache.tinkerpop.gremlin.process.traversal.TextP
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.services.{IntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.scalligraph.utils.FunctionalCondition.When
import org.thp.thehive.models.{AlertTag, CaseTag, ObservableTag, Organisation, OrganisationTaxonomy, Tag, Taxonomy, TaxonomyTag}
import org.thp.thehive.services.TagOps._
import org.thp.thehive.services.OrganisationOps._

import javax.inject.{Inject, Named, Singleton}
import scala.util.{Success, Try}

@Singleton
class TagSrv @Inject() (organisationSrv: OrganisationSrv, appConfig: ApplicationConfig, @Named("integrity-check-actor") integrityCheckActor: ActorRef)
    extends VertexSrv[Tag] {

  private val freeTagColourConfig: ConfigItem[String, String] =
    appConfig.item[String]("tags.freeTagColour", "Default colour for free tags")

  def freeTagColour: String = freeTagColourConfig.get

  def freeTagNamespace(implicit graph: Graph, authContext: AuthContext): String =
    s"_freetags_${organisationSrv.currentId(graph, authContext).value}"

  def parseString(tagName: String)(implicit graph: Graph, authContext: AuthContext): Tag = {
    val ns  = freeTagNamespace
    val tag = Tag.fromString(tagName, ns, freeTagColour)
    if (tag.isFreeTag) Tag(ns, tagName, None, None, freeTagColour)
    else tag
  }

  def getTag(tag: Tag)(implicit graph: Graph): Traversal.V[Tag] = startTraversal.getTag(tag)

  def getOrCreate(tagName: String)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] = {
    val tag = parseString(tagName)
    getTag(tag).headOption.fold(create(tag))(Success(_))
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

    def taxonomy: Traversal.V[Taxonomy] = traversal.in[TaxonomyTag].v[Taxonomy]

    def organisation: Traversal.V[Organisation]                           = traversal.in[TaxonomyTag].in[OrganisationTaxonomy].v[Organisation]
    def displayName: Traversal[String, Vertex, Converter[String, Vertex]] = traversal.domainMap(_.toString)

    def fromCase: Traversal.V[Tag] = traversal.filter(_.in[CaseTag])

    def fromObservable: Traversal.V[Tag] = traversal.filter(_.in[ObservableTag])

    def fromAlert: Traversal.V[Tag] = traversal.filter(_.in[AlertTag])

    def autoComplete(organisationSrv: OrganisationSrv, freeTag: String)(implicit authContext: AuthContext): Traversal.V[Tag] = {
      val freeTagNamespace: String = s"_freetags_${organisationSrv.currentId(traversal.graph, authContext).value}"
      traversal
        .has(_.namespace, freeTagNamespace)
        .has(_.predicate, TextP.containing(freeTag))
    }
    def autoComplete(namespace: Option[String], predicate: Option[String], value: Option[String])(implicit
        authContext: AuthContext
    ): Traversal.V[Tag] = {
      traversal.graph.db.mapPredicate(TextP.containing(""))
      traversal
        .merge(namespace)((t, ns) => t.has(_.namespace, TextP.containing(ns)))
        .merge(predicate)((t, p) => t.has(_.predicate, TextP.containing(p)))
        .merge(value)((t, v) => t.has(_.value, TextP.containing(v)))
        .visible
    }

    def visible(implicit authContext: AuthContext): Traversal.V[Tag] =
      traversal.filter(_.organisation.current)
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
