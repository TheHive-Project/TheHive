package org.thp.thehive.services

import org.apache.tinkerpop.gremlin.process.traversal.TextP
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.scalligraph.traversal.Converter.Identity
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.scalligraph.utils.FunctionalCondition.When
import org.thp.scalligraph.{BadRequestError, EntityId, EntityIdOrName, RichSeq}
import org.thp.thehive.models._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.TagOps._
import org.thp.thehive.services.TaxonomyOps._

import java.util.{Date, Map => JMap}
import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton
class TaxonomySrv @Inject() (organisationSrv: OrganisationSrv, tagSrv: TagSrv) extends VertexSrv[Taxonomy] {

  val taxonomyTagSrv          = new EdgeSrv[TaxonomyTag, Taxonomy, Tag]
  val organisationTaxonomySrv = new EdgeSrv[OrganisationTaxonomy, Organisation, Taxonomy]

  def create(taxo: Taxonomy, tags: Seq[Tag with Entity])(implicit graph: Graph, authContext: AuthContext): Try[RichTaxonomy] =
    for {
      taxonomy     <- createEntity(taxo)
      _            <- tags.toTry(t => taxonomyTagSrv.create(TaxonomyTag(), taxonomy, t))
      richTaxonomy <- Try(RichTaxonomy(taxonomy, tags))
    } yield richTaxonomy

  def createFreetagTaxonomy(organisation: Organisation with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Taxonomy with Entity] = {
    val customTaxo = Taxonomy(s"_freetags_${organisation._id.value}", "Custom taxonomy", 1)
    for {
      taxonomy <- createEntity(customTaxo)
      _        <- organisationTaxonomySrv.create(OrganisationTaxonomy(), organisation, taxonomy)
    } yield taxonomy
  }

  def getFreetagTaxonomy(implicit graph: Graph, authContext: AuthContext): Try[Taxonomy with Entity] =
    getByName(s"_freetags_${organisationSrv.currentId.value}")
      .getOrFail("FreetagTaxonomy")
      .orElse {
        organisationSrv.current.notAdmin.getOrFail("Organisation").flatMap(createFreetagTaxonomy)
      }

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Taxonomy] =
    startTraversal.getByNamespace(name)

  def update(taxonomy: Taxonomy with Entity, input: Taxonomy)(implicit graph: Graph, authContext: AuthContext): Try[RichTaxonomy] =
    for {
      updatedTaxonomy <-
        get(taxonomy)
          .when(taxonomy.namespace != input.namespace)(_.update(_.namespace, input.namespace))
          .when(taxonomy.description != input.description)(_.update(_.description, input.description))
          .when(taxonomy.version != input.version)(_.update(_.version, input.version))
          .when(input != taxonomy)(_.update(_._updatedAt, Some(new Date)).update(_._updatedBy, Some(authContext.userId)))
          .richTaxonomy
          .getOrFail("Taxonomy")
    } yield updatedTaxonomy

  def updateOrCreateTag(namespace: String, t: Tag)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] =
    if (getByName(namespace).doesTagExists(t))
      for {
        tag        <- tagSrv.getTag(t).getOrFail("Tag")
        updatedTag <- tagSrv.update(tag, t)
      } yield updatedTag
    else
      for {
        tag  <- tagSrv.create(t)
        taxo <- getByName(namespace).getOrFail("Taxonomy")
        _    <- taxonomyTagSrv.create(TaxonomyTag(), taxo, tag)
      } yield tag

  def activate(taxonomyId: EntityIdOrName)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      taxo <- get(taxonomyId).getOrFail("Taxonomy")
      _ <-
        if (taxo.namespace.startsWith("_freetags")) Failure(BadRequestError("Cannot activate a freetags taxonomy"))
        else Success(())
      _ <-
        organisationSrv
          .startTraversal
          .filterNot(_.out[OrganisationTaxonomy].v[Taxonomy].has(_.namespace, taxo.namespace))
          .toSeq
          .toTry(o => organisationTaxonomySrv.create(OrganisationTaxonomy(), o, taxo))
    } yield ()

  def deactivate(taxonomyId: EntityIdOrName)(implicit graph: Graph): Try[Unit] =
    for {
      taxo <- getOrFail(taxonomyId)
      _ <-
        if (taxo.namespace.startsWith("_freetags")) Failure(BadRequestError("Cannot deactivate a freetags taxonomy"))
        else Success(())
    } yield get(taxonomyId).inE[OrganisationTaxonomy].remove()

}

object TaxonomyOps {
  implicit class TaxonomyOpsDefs(traversal: Traversal.V[Taxonomy]) {

    def get(idOrName: EntityId): Traversal.V[Taxonomy] =
      idOrName.fold(traversal.getByIds(_), getByNamespace)

    def getByNamespace(namespace: String): Traversal.V[Taxonomy] = traversal.has(_.namespace, namespace)

    def visible(implicit authContext: AuthContext): Traversal.V[Taxonomy] =
      if (authContext.isPermitted(Permissions.manageTaxonomy))
        noFreetags
      else
        noFreetags.filter(_.organisations.get(authContext.organisation))

    def noFreetags: Traversal.V[Taxonomy] =
      traversal.hasNot(_.namespace, TextP.startingWith("_freetags"))

    def freetags: Traversal.V[Taxonomy] =
      traversal.has(_.namespace, TextP.startingWith("_freetags"))

    def alreadyImported(namespace: String): Boolean =
      traversal.getByNamespace(namespace).exists

    def organisations: Traversal.V[Organisation] = traversal.in[OrganisationTaxonomy].v[Organisation]

    def enabled: Traversal[Boolean, Boolean, Identity[Boolean]] =
      traversal.choose(_.organisations, true, false)

    def tags: Traversal.V[Tag] = traversal.out[TaxonomyTag].v[Tag]

    def doesTagExists(tag: Tag): Boolean = traversal.tags.getTag(tag).exists

    def richTaxonomy: Traversal[RichTaxonomy, JMap[String, Any], Converter[RichTaxonomy, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.tags.fold)
        )
        .domainMap { case (taxonomy, tags) => RichTaxonomy(taxonomy, tags) }

    def richTaxonomyWithCustomRenderer[D, G, C <: Converter[D, G]](
        entityRenderer: Traversal.V[Taxonomy] => Traversal[D, G, C]
    ): Traversal[(RichTaxonomy, D), JMap[String, Any], Converter[(RichTaxonomy, D), JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.tags.fold)
            .by(entityRenderer)
        )
        .domainMap {
          case (taxo, tags, renderedEntity) =>
            RichTaxonomy(
              taxo,
              tags
            ) -> renderedEntity
        }
  }
}
