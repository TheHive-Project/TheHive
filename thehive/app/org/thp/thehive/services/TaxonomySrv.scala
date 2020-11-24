package org.thp.thehive.services

import java.util.{Map => JMap}

import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.scalligraph.{EntityId, EntityIdOrName, RichSeq}
import org.thp.thehive.models._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.TaxonomyOps._

import scala.util.{Success, Try}

@Singleton
class TaxonomySrv @Inject() (
  organisationSrv: OrganisationSrv
)(implicit @Named("with-thehive-schema") db: Database
) extends VertexSrv[Taxonomy] {

  val taxonomyTagSrv = new EdgeSrv[TaxonomyTag, Taxonomy, Tag]
  val organisationTaxonomySrv = new EdgeSrv[OrganisationTaxonomy, Organisation, Taxonomy]

  def existsInOrganisation(namespace: String)(implicit graph: Graph, authContext: AuthContext): Boolean = {
    startTraversal
          .has(_.namespace, namespace)
          .in[OrganisationTaxonomy]
          .v[Organisation]
          .current
          .exists
  }

  def create(taxo: Taxonomy, tags: Seq[Tag with Entity])(implicit graph: Graph, authContext: AuthContext): Try[RichTaxonomy] =
    for {
      taxonomy     <- createEntity(taxo)
      _            <- tags.toTry(t => taxonomyTagSrv.create(TaxonomyTag(), taxonomy, t))
      richTaxonomy <- Try(RichTaxonomy(taxonomy, tags))
      _            <- activate(richTaxonomy._id)
    } yield richTaxonomy

  def createFreetag(organisation: Organisation with Entity)(implicit graph: Graph, authContext: AuthContext): Try[RichTaxonomy] = {
    val customTaxo = Taxonomy("_freetags", "Custom taxonomy", 1)
    for {
      taxonomy     <- createEntity(customTaxo)
      richTaxonomy <- Try(RichTaxonomy(taxonomy, Seq()))
      _            <- organisationTaxonomySrv.create(OrganisationTaxonomy(), organisation, taxonomy)
    } yield richTaxonomy
  }

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Taxonomy] =
    Try(startTraversal.getByNamespace(name)).getOrElse(startTraversal.limit(0))

  def activate(taxonomyId: EntityIdOrName)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      taxo          <- get(taxonomyId).getOrFail("Taxonomy")
      organisations <- Try(organisationSrv.startTraversal.filterNot(_
        .out[OrganisationTaxonomy]
        .filter(_.unsafeHas("namespace", taxo.namespace))
      ).toSeq)
      _ <- organisations.toTry(o => organisationTaxonomySrv.create(OrganisationTaxonomy(), o, taxo))
    } yield Success(())

  def deactivate(taxonomyId: EntityIdOrName)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      taxo <- get(taxonomyId).getOrFail("Taxonomy")
      _    <- Try(organisationSrv.startTraversal
        .filterNot(_.unsafeHas("name", "admin"))
        .outE[OrganisationTaxonomy]
        .filter(_.otherV().unsafeHas("namespace", taxo.namespace))
        .remove())
    } yield Success(())

}

object TaxonomyOps {
  implicit class TaxonomyOpsDefs(traversal: Traversal.V[Taxonomy]) {

    def get(idOrName: EntityId): Traversal.V[Taxonomy] =
      traversal.getByIds(idOrName)

    def getByNamespace(namespace: String): Traversal.V[Taxonomy] = traversal.has(_.namespace, namespace)

    def visible(implicit authContext: AuthContext): Traversal.V[Taxonomy] = visible(authContext.organisation)

    def visible(organisationIdOrName: EntityIdOrName): Traversal.V[Taxonomy] =
      traversal.filter(_.organisations.get(organisationIdOrName))

    def organisations: Traversal.V[Organisation] = traversal.in[OrganisationTaxonomy].v[Organisation]

    def tags: Traversal.V[Tag] = traversal.out[TaxonomyTag].v[Tag]

    def richTaxonomy: Traversal[RichTaxonomy, JMap[String, Any], Converter[RichTaxonomy, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.tags.fold)
        )
        .domainMap { case (taxonomy, tags) => RichTaxonomy(taxonomy, tags) }
  }
}
