package org.thp.thehive.services

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity, TextPredicate}
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.services.{EdgeSrv, EntitySelector, IntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.scalligraph.utils.FunctionalCondition.When
import org.thp.thehive.models._

import java.util.{Date, Map => JMap}
import scala.util.matching.Regex
import scala.util.{Success, Try}

class TagSrv(
    _organisationSrv: => OrganisationSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val customFieldValueSrv: CustomFieldValueSrv,
    taxonomySrv: TaxonomySrv,
    appConfig: ApplicationConfig,
    integrityCheckActor: => ActorRef @@ IntegrityCheckTag
) extends VertexSrv[Tag]
    with TheHiveOps {
  override lazy val organisationSrv: OrganisationSrv = _organisationSrv

  val taxonomyTagSrv = new EdgeSrv[TaxonomyTag, Taxonomy, Tag]
  private lazy val freeTagColourConfig: ConfigItem[String, String] =
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
    startTraversal.getFreetag(idOrName)

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

trait TagOpsNoDeps { _: TheHiveOpsNoDeps =>

  implicit class TagOpsNoDepsDefs(traversal: Traversal.V[Tag]) {

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

    def autoComplete(organisationSrv: OrganisationSrv, freeTag: String)(implicit authContext: AuthContext): Traversal.V[Tag] =
      freetags(organisationSrv)
        .has(_.predicate, TextPredicate.contains(freeTag))

    def autoComplete(namespace: Option[String], predicate: Option[String], value: Option[String])(implicit
        authContext: AuthContext
    ): Traversal.V[Tag] =
      traversal
        .merge(namespace)((t, ns) => t.has(_.namespace, TextPredicate.contains(ns)))
        .merge(predicate)((t, p) => t.has(_.predicate, TextPredicate.contains(p)))
        .merge(value)((t, v) => t.has(_.value, TextPredicate.contains(v)))
        .visible

    def visible(implicit authContext: AuthContext): Traversal.V[Tag] =
      traversal.filter(_.organisation.current)

    def withCustomRenderer[D, G, C <: Converter[D, G]](
        entityRenderer: Traversal.V[Tag] => Traversal[D, G, C]
    ): Traversal[(Tag with Entity, D), JMap[String, Any], Converter[(Tag with Entity, D), JMap[String, Any]]] =
      traversal.project(_.by.by(entityRenderer))
  }
}
trait TagOps { _: TheHiveOpsNoDeps =>
  protected val organisationSrv: OrganisationSrv
  implicit class TagOpsDefs(traversal: Traversal.V[Tag]) {
    def getFreetag(idOrName: EntityIdOrName)(implicit authContext: AuthContext): Traversal.V[Tag] =
      idOrName.fold(traversal.getByIds(_), traversal.has(_.predicate, _)).freetags(organisationSrv)

  }
}
class TagIntegrityCheckOps(val db: Database, val service: TagSrv) extends IntegrityCheckOps[Tag] with TheHiveOpsNoDeps {

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

  override def globalCheck(): Map[String, Int] =
    db.tryTransaction { implicit graph =>
      Try {
        val orphans = service
          .startTraversal
          .filter(_.taxonomy.has(_.namespace, TextPredicate.startsWith("_freetags_")))
          .filterNot(_.or(_.inE[AlertTag], _.inE[ObservableTag], _.inE[CaseTag], _.inE[CaseTemplateTag]))
          ._id
          .toSeq
        if (orphans.nonEmpty) {
          service.getByIds(orphans: _*).remove()
          Map("orphan" -> orphans.size)
        } else Map.empty[String, Int]
      }
    }.getOrElse(Map("globalFailure" -> 1))
}
