package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named}
import org.thp.scalligraph.{EntityIdOrName, RichSeq}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputTaxonomy
import org.thp.thehive.models.{Permissions, RichTaxonomy, Tag, Taxonomy}
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.TaxonomyOps._
import org.thp.thehive.services.{OrganisationSrv, TagSrv, TaxonomySrv}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

class TaxonomyCtrl @Inject() (
  entrypoint: Entrypoint,
  properties: Properties,
  taxonomySrv: TaxonomySrv,
  organisationSrv: OrganisationSrv,
  tagSrv: TagSrv,
  @Named("with-thehive-schema") implicit val db: Database
) extends QueryableCtrl {

  override val entityName: String = "taxonomy"
  override val publicProperties: PublicProperties = properties.taxonomy
  override val initialQuery: Query =
    Query.init[Traversal.V[Taxonomy]]("listTaxonomy", (graph, authContext) =>
      organisationSrv.get(authContext.organisation)(graph).taxonomies
    )
  override val getQuery: ParamQuery[EntityIdOrName] =
    Query.initWithParam[EntityIdOrName, Traversal.V[Taxonomy]](
      "getTaxonomy",
      FieldsParser[EntityIdOrName],
      (idOrName, graph, authContext) => taxonomySrv.get(idOrName)(graph).visible(authContext)
    )
  override val pageQuery: ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Taxonomy], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, traversal, _) =>
      traversal.richPage(range.from, range.to, range.extraData.contains("total"))(_.richTaxonomy)
  )
  override val outputQuery: Query =
    Query.outputWithContext[RichTaxonomy, Traversal.V[Taxonomy]]((traversal, _) =>
    traversal.richTaxonomy
  )
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Taxonomy], Traversal.V[Tag]]("tags", (traversal, _) => traversal.tags)
  )

  def list: Action[AnyContent] =
    entrypoint("list taxonomies")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val taxos = taxonomySrv
          .startTraversal
          .visible
          .richTaxonomy
          .toSeq
        Success(Results.Ok(taxos.toJson))
      }

  def create: Action[AnyContent] =
    entrypoint("import taxonomy")
      .extract("taxonomy", FieldsParser[InputTaxonomy])
      .authPermittedTransaction(db, Permissions.manageTaxonomy) { implicit request => implicit graph =>
        val inputTaxo: InputTaxonomy = request.body("taxonomy")

        val taxonomy = Taxonomy(inputTaxo.namespace, inputTaxo.description, inputTaxo.version)

        // Create tags
        val tagValues = inputTaxo.values.getOrElse(Seq())
        val tags = tagValues.foldLeft(Seq[Tag]())((all, value) => {
            all ++ value.entry.map(e =>
              Tag(inputTaxo.namespace,
                  value.predicate,
                  Some(e.value),
                  Some(e.expanded),
                  e.colour.map(tagSrv.parseTagColour).getOrElse(tagSrv.defaultColour)
              )
            )
        })

        // Create a tag for predicates with no tags associated
        val predicateWithNoTags = inputTaxo.predicates.diff(tagValues.map(_.predicate))
        val allTags = tags ++ predicateWithNoTags.map(p =>
          Tag(inputTaxo.namespace, p, None, None, tagSrv.defaultColour)
        )

        for {
          tagsEntities <- allTags.toTry(t => tagSrv.create(t))
          richTaxonomy <- taxonomySrv.create(taxonomy, tagsEntities)
        } yield Results.Created(richTaxonomy.toJson)
  }

  def get(taxonomyId: String): Action[AnyContent] =
    entrypoint("get taxonomy")
      .authRoTransaction(db) { implicit request => implicit graph =>
        taxonomySrv
          .get(EntityIdOrName(taxonomyId))
          .visible
          .richTaxonomy
          .getOrFail("Taxonomy")
          .map(taxonomy => Results.Ok(taxonomy.toJson))
      }

/*
  def delete(namespace: String): Action[AnyContent] =
    entrypoint("delete taxonomy")
      .authTransaction(db) { implicit request => implicit  graph =>
        for {
          t <- taxonomySrv.getByNamespace(namespace)

        } yield Results.Nocontent
      }
*/

}
