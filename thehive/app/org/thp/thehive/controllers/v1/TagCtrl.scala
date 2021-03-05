package org.thp.thehive.controllers.v1

import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, IteratorOutput, Traversal}
import org.thp.scalligraph.utils.FunctionalCondition.When
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.{Permissions, Tag}
import org.thp.thehive.services.TagOps._
import org.thp.thehive.services.{OrganisationSrv, TagSrv}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.Inject

case class TagHint(freeTag: Option[String], namespace: Option[String], predicate: Option[String], value: Option[String], limit: Option[Long])

class TagCtrl @Inject() (
    entrypoint: Entrypoint,
    db: Database,
    tagSrv: TagSrv,
    organisationSrv: OrganisationSrv,
    properties: Properties
) extends QueryableCtrl {
  override val entityName: String                 = "Tag"
  override val publicProperties: PublicProperties = properties.tag
  override val initialQuery: Query =
    Query.init[Traversal.V[Tag]]("listTag", (graph, authContext) => tagSrv.startTraversal(graph).visible(authContext))
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Tag], IteratorOutput](
    "page",
    (range, tagSteps, _) => tagSteps.page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.output[Tag with Entity]
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Tag]](
    "getTag",
    (idOrName, graph, _) => tagSrv.get(idOrName)(graph)
  )
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Tag], Traversal.V[Tag]]("freetags", (tagSteps, authContext) => tagSteps.freetags(organisationSrv)(authContext)),
    Query.initWithParam[TagHint, Traversal[String, Vertex, Converter[String, Vertex]]](
      "tagAutoComplete",
      (tagHint, graph, authContext) =>
        tagHint
          .freeTag
          .fold(tagSrv.startTraversal(graph).autoComplete(tagHint.namespace, tagHint.predicate, tagHint.value)(authContext).visible(authContext))(
            tagSrv.startTraversal(graph).autoComplete(organisationSrv, _)(authContext).sort(_.by("predicate", Order.asc))
          )
          .merge(tagHint.limit)(_.limit(_))
          .displayName
    ),
    Query[Traversal.V[Tag], Traversal[String, Vertex, Converter[String, Vertex]]]("text", (tagSteps, _) => tagSteps.displayName),
    Query.output[String, Traversal[String, Vertex, Converter[String, Vertex]]]
  )

  def get(tagId: String): Action[AnyContent] =
    entrypoint("get tag")
      .authRoTransaction(db) { _ => implicit graph =>
        tagSrv
          .getOrFail(EntityIdOrName(tagId))
          .map(tag => Results.Ok(tag.toJson))
      }

  def update(tagId: String): Action[AnyContent] =
    entrypoint("update tag")
      .extract("tag", FieldsParser.update("tag", publicProperties))
      .authPermittedTransaction(db, Permissions.manageTag) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("tag")
        tagSrv
          .update(_.getFreetag(organisationSrv, EntityIdOrName(tagId)), propertyUpdaters)
          .map(_ => Results.NoContent)
      }
}
