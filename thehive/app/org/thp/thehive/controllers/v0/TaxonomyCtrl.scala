package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FFile, FieldsParser}
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query.{ParamQuery, PublicProperties, PublicPropertyListBuilder, Query, QueryExecutor}
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion.taxonomyOutput
import org.thp.thehive.dto.v1.InputTaxonomy
import org.thp.thehive.models.{Permissions, RichTaxonomy, Taxonomy}
import org.thp.thehive.services.{OrganisationSrv, TaxonomySrv}
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.TaxonomyOps._
import play.api.mvc.{Action, AnyContent}

class TaxonomyCtrl @Inject() (
  override val entrypoint: Entrypoint,
  @Named("with-thehive-schema") override val db: Database,
  @Named("v0") override val queryExecutor: QueryExecutor,
  override val publicData: PublicTaxonomy
) extends QueryCtrl {
  def importTaxonomy: Action[AnyContent] =
    entrypoint("import taxonomy")
    .extract("file", FieldsParser.file.optional.on("file"))
    .extract("taxonomy", FieldsParser[InputTaxonomy])
    .authPermittedTransaction(db, Permissions.manageTaxonomy) { implicit request => implicit graph =>
    val file: Option[FFile]       = request.body("file")
    val taxonomy: InputTaxonomy   = request.body("taxonomy")

    // Create Taxonomy vertex
    // Create Tags associated
    // Add edge orgaTaxo

    ???
  }

}

@Singleton
class PublicTaxonomy @Inject() (
  taxonomySrv: TaxonomySrv,
  organisationSrv: OrganisationSrv
) extends PublicData {
  override val entityName: String = "taxonomy"
  override val initialQuery: Query =
    Query.init[Traversal.V[Taxonomy]]("listTaxonomy", (graph, authContext) =>
      organisationSrv.get(authContext.organisation)(graph).taxonomies
    )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Taxonomy], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, taxoSteps, _) => taxoSteps.page(range.from, range.to, withTotal = true)(???)
  )
  override val outputQuery: Query = Query.outputWithContext[RichTaxonomy, Traversal.V[Taxonomy]]((taxonomySteps, authContext) =>
    taxonomySteps.richTaxonomy(authContext)
  )
  override val getQuery: ParamQuery[EntityIdOrName] =
    Query.initWithParam[EntityIdOrName, Traversal.V[Taxonomy]](
      "getTaxonomy",
      FieldsParser[EntityIdOrName],
      (idOrName, graph, authContext) => taxonomySrv.get(idOrName)(graph).visible(authContext)
    )
  override val publicProperties: PublicProperties =
    PublicPropertyListBuilder[Taxonomy]
      .property("namespace", UMapping.string)(_.field.readonly)
      .property("description", UMapping.string)(_.field.readonly)
      .property("version", UMapping.int)(_.field.readonly)
      // Predicates ?
      // Values ?
      .build

}