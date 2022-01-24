package org.thp.thehive.services

import akka.actor.ActorRef
import org.apache.tinkerpop.gremlin.process.traversal.TextP
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.services.{EdgeSrv, EntitySelector, IntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.scalligraph.utils.FunctionalCondition.When
import org.thp.thehive.models._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.TagOps._

import java.util.{Date, Map => JMap}
import javax.inject.{Inject, Named, Provider, Singleton}
import scala.util.matching.Regex
import scala.util.{Success, Try}

@Singleton
class TagSrv @Inject() (
    organisationSrv: OrganisationSrv,
    taxonomySrvProvider: Provider[TaxonomySrv],
    appConfig: ApplicationConfig,
    @Named("integrity-check-actor") integrityCheckActor: ActorRef
) extends VertexSrv[Tag] {
  lazy val taxonomySrv: TaxonomySrv = taxonomySrvProvider.get

  val taxonomyTagSrv = new EdgeSrv[TaxonomyTag, Taxonomy, Tag]
  private val freeTagColourConfig: ConfigItem[String, String] =
    appConfig.item[String]("tags.freeTagColour", "Default colour for free tags")

  def freeTagColour: String = freeTagColourConfig.get

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

  def getFreetag(idOrName: EntityIdOrName)(implicit graph: Graph, authContext: AuthContext): Traversal.V[Tag] =
    startTraversal.getFreetag(organisationSrv, idOrName)

  def getOrCreate(tagName: String)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] =
    fromString(tagName)
      .flatMap {
        case (ns, pred, v) => startTraversal.getByName(ns, pred, v).headOption
      }
      .fold {
        startTraversal
          .getByName(freeTagNamespace, tagName, None)
          .getOrFail("Tag")
          .orElse(createFreeTag(tagName))
      }(Success(_))

  private def createFreeTag(tagName: String)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] =
    for {
      freetagTaxonomy <- taxonomySrv.getFreetagTaxonomy
      tag             <- createEntity(Tag(freeTagNamespace, tagName, None, None, freeTagColour))
      _               <- taxonomyTagSrv.create(TaxonomyTag(), freetagTaxonomy, tag)
    } yield tag

  def create(tag: Tag)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] = {
    integrityCheckActor ! EntityAdded("Tag")
    super.createEntity(tag)
  }

  override def exists(e: Tag)(implicit graph: Graph): Boolean = startTraversal.getByName(e.namespace, e.predicate, e.value).exists

  def update(
      tag: Tag with Entity,
      input: Tag
  )(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] =
    for {
      updatedTag <- get(tag)
        .when(tag.description != input.description)(_.update(_.description, input.description))
        .when(tag.colour != input.colour)(_.update(_.colour, input.colour))
        .when(input != tag)(_.update(_._updatedAt, Some(new Date)).update(_._updatedBy, Some(authContext.userId)))
        .getOrFail("Tag")
    } yield updatedTag

  // TODO add audit
  override def delete(tag: Tag with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val tagName = tag.toString
    Try {
      get(tag)
        .sideEffect(
          _.unionFlat(
            _.`case`.removeValue(_.tags, tagName),
            _.alert.removeValue(_.tags, tagName),
            _.observable.removeValue(_.tags, tagName),
            _.caseTemplate.removeValue(_.tags, tagName)
          )
        )
        .remove()
    }
  }
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
    def `case`: Traversal.V[Case]  = traversal.in[CaseTag].v[Case]

    def fromObservable: Traversal.V[Tag]    = traversal.filter(_.in[ObservableTag])
    def observable: Traversal.V[Observable] = traversal.in[ObservableTag].v[Observable]

    def fromAlert: Traversal.V[Tag] = traversal.filter(_.in[AlertTag])
    def alert: Traversal.V[Alert]   = traversal.in[AlertTag].v[Alert]

    def fromCaseTemplate: Traversal.V[Tag]      = traversal.filter(_.in[CaseTemplateTag])
    def caseTemplate: Traversal.V[CaseTemplate] = traversal.in[CaseTemplateTag].v[CaseTemplate]

    def freetags(organisationSrv: OrganisationSrv)(implicit authContext: AuthContext): Traversal.V[Tag] = {
      val freeTagNamespace: String = s"_freetags_${organisationSrv.currentId(traversal.graph, authContext).value}"
      traversal
        .has(_.namespace, freeTagNamespace)
    }

    def getFreetag(organisationSrv: OrganisationSrv, idOrName: EntityIdOrName)(implicit authContext: AuthContext): Traversal.V[Tag] =
      idOrName.fold(traversal.getByIds(_), traversal.has(_.predicate, _)).freetags(organisationSrv)

    def autoComplete(organisationSrv: OrganisationSrv, freeTag: String)(implicit authContext: AuthContext): Traversal.V[Tag] =
      freetags(organisationSrv)
        .has(_.predicate, TextP.containing(freeTag))

    def autoComplete(namespace: Option[String], predicate: Option[String], value: Option[String])(implicit
        authContext: AuthContext
    ): Traversal.V[Tag] =
      traversal
        .merge(namespace)((t, ns) => t.has(_.namespace, TextP.containing(ns)))
        .merge(predicate)((t, p) => t.has(_.predicate, TextP.containing(p)))
        .merge(value)((t, v) => t.has(_.value, TextP.containing(v)))
        .visible

    def visible(implicit authContext: AuthContext): Traversal.V[Tag] =
      traversal.filter(_.organisation.current)

    def withCustomRenderer[D, G, C <: Converter[D, G]](
        entityRenderer: Traversal.V[Tag] => Traversal[D, G, C]
    ): Traversal[(Tag with Entity, D), JMap[String, Any], Converter[(Tag with Entity, D), JMap[String, Any]]] =
      traversal.project(_.by.by(entityRenderer))
  }
}

class TagIntegrityCheckOps @Inject() (val db: Database, val service: TagSrv) extends IntegrityCheckOps[Tag] {

  override def resolve(entities: Seq[Tag with Entity])(implicit graph: Graph): Try[Unit] = {
    EntitySelector.firstCreatedEntity(entities).foreach {
      case (head, tail) =>
        tail.foreach(copyEdge(_, head))
        val tailIds = tail.map(_._id)
        logger.debug(s"Remove duplicated vertex: ${tailIds.mkString(",")}")
        service.getByIds(tailIds: _*).remove()
    }
    Success(())
  }

  override def globalCheck(stopAt: Long): Map[String, Int] =
    service
      .pagedTraversalIds(db, 100, _.filterNot(_.or(_.alert, _.observable, _.`case`, _.caseTemplate, _.taxonomy))) { ids =>
        if (System.currentTimeMillis() > stopAt) None
        else
          Some {
            db.tryTransaction { implicit graph =>
              Try {
                val orphans = service
                  .getByIds(ids: _*)
                  ._id
                  .toSeq
                if (orphans.nonEmpty) {
                  service.getByIds(orphans: _*).remove()
                  Map("orphan" -> orphans.size)
                } else Map.empty[String, Int]
              }
            }.getOrElse(Map("globalFailure" -> 1))
          }
      }
      .reduceOption(_ <+> _)
      .getOrElse(Map.empty)
}
