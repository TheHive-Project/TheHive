package org.thp.thehive.services

import java.util.{Map => JMap}

import javax.inject.{Inject, Named}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.{EntityIdOrName, RichSeq}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.thehive.models.{Organisation, OrganisationTaxonomy, Predicate, RichTaxonomy, Tag, Taxonomy, TaxonomyTag, Value}
import org.thp.thehive.services.OrganisationOps._

import scala.util.Try

@Singleton
class TaxonomySrv @Inject() (
)(implicit
  @Named("with-thehive-schema") db: Database
) extends VertexSrv[Taxonomy] {

  val taxonomyTagSrv = new EdgeSrv[TaxonomyTag, Taxonomy, Tag]

  def create(taxo: Taxonomy, tags: Seq[Tag with Entity])(implicit graph: Graph, authContext: AuthContext): Try[RichTaxonomy] =
    for {
      taxonomy <- createEntity(taxo)
      _ <- tags.toTry(t => taxonomyTagSrv.create(TaxonomyTag(), taxonomy, t))
      richTaxonomy <- RichTaxonomy(taxonomy, ???, ???)
    } yield richTaxonomy
}

object TaxonomyOps {
  implicit class TaxonomyOpsDefs(traversal: Traversal.V[Taxonomy]) {

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
        .domainMap {
          case (taxonomy, tags) =>
            val predicates = tags.map(t => Predicate(t.predicate)).distinct
            val values = predicates.map { p =>
              val tagValues = tags
                .filter(_.predicate == p.value)
                .filter(_.value.isDefined)
                .map(_.value.get)
              Value(p.value, tagValues)
            }
            RichTaxonomy(taxonomy, predicates, values)
        }

  }
}
