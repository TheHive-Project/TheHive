package org.thp.thehive.services

import java.util.{Map => JMap}

import javax.inject.{Inject, Named}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.scalligraph.{EntityIdOrName, RichSeq}
import org.thp.thehive.models._
import org.thp.thehive.services.OrganisationOps._

import scala.util.Try

@Singleton
class TaxonomySrv @Inject() (
  organisationSrv: OrganisationSrv,
  tagSrv: TagSrv
)(implicit @Named("with-thehive-schema") db: Database
) extends VertexSrv[Taxonomy] {

  val taxonomyTagSrv = new EdgeSrv[TaxonomyTag, Taxonomy, Tag]
  val organisationTaxonomySrv = new EdgeSrv[OrganisationTaxonomy, Organisation, Taxonomy]

  def create(taxo: Taxonomy, tags: Seq[Tag with Entity])(implicit graph: Graph, authContext: AuthContext): Try[RichTaxonomy] =
    for {
      taxonomy     <- createEntity(taxo)
      organisation <- organisationSrv.getOrFail(authContext.organisation)
      _            <- organisationTaxonomySrv.create(OrganisationTaxonomy(), organisation, taxonomy)
      _            <- tags.toTry(t => taxonomyTagSrv.create(TaxonomyTag(), taxonomy, t))
      richTaxonomy <- Try(RichTaxonomy(taxonomy, tags))
    } yield richTaxonomy
/*

  def getByNamespace(namespace: String)(implicit graph: Graph): Traversal.V[Taxonomy] =
    Try(startTraversal.getByNamespace(namespace)).getOrElse(startTraversal.limit(0))
*/

}

object TaxonomyOps {
  implicit class TaxonomyOpsDefs(traversal: Traversal.V[Taxonomy]) {

    def getByNamespace(namespace: String): Traversal.V[Taxonomy] = traversal.has(_.namespace, namespace)

    def visible(implicit authContext: AuthContext): Traversal.V[Taxonomy] = visible(authContext.organisation)

    def visible(organisationIdOrName: EntityIdOrName): Traversal.V[Taxonomy] =
      traversal.filter(_.organisations.get(organisationIdOrName))

    def organisations: Traversal.V[Organisation] = traversal.in[OrganisationTaxonomy].v[Organisation]

    def tags: Traversal.V[Tag] = traversal.out[TaxonomyTag].v[Tag]

    def richTaxonomy(implicit authContext: AuthContext): Traversal[RichTaxonomy, JMap[String, Any], Converter[RichTaxonomy, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.tags.fold)
        )
        .domainMap { case (taxonomy, tags) => RichTaxonomy(taxonomy, tags) }
  }
}
