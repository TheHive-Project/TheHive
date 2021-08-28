package org.thp.thehive.controllers.v0

import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, Renderer}
import org.thp.scalligraph.models.{Database, Entity, IndexType, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.{Converter, IteratorOutput, Traversal}
import org.thp.scalligraph.utils.FunctionalCondition.When
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.models.Tag
import org.thp.thehive.services.{OrganisationSrv, SearchSrv, TagSrv, TheHiveOpsNoDeps}
import play.api.mvc.{Action, AnyContent, Results}

class TagCtrl(
    override val entrypoint: Entrypoint,
    override val db: Database,
    tagSrv: TagSrv,
    override val queryExecutor: QueryExecutor,
    override val publicData: PublicTag
) extends QueryCtrl {

  def get(tagId: String): Action[AnyContent] =
    entrypoint("get tag")
      .authRoTransaction(db) { _ => implicit graph =>
        tagSrv
          .getOrFail(EntityIdOrName(tagId))
          .map { tag =>
            Results.Ok(tag.toJson)
          }
      }
}

case class TagHint(freeTag: Option[String], namespace: Option[String], predicate: Option[String], value: Option[String], limit: Option[Long])

class PublicTag(tagSrv: TagSrv, organisationSrv: OrganisationSrv, searchSrv: SearchSrv) extends PublicData with TheHiveOpsNoDeps {
  override val entityName: String = "tag"
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
  implicit val stringRenderer: Renderer[String] = Renderer.toJson[String, String](identity)
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Tag], Traversal.V[Tag]]("fromCase", (tagSteps, _) => tagSteps.fromCase),
    Query[Traversal.V[Tag], Traversal.V[Tag]]("fromObservable", (tagSteps, _) => tagSteps.fromObservable),
    Query[Traversal.V[Tag], Traversal.V[Tag]]("fromAlert", (tagSteps, _) => tagSteps.fromAlert),
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
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[Tag]
    .property("keyword", UMapping.string)(
      _.select(_.empty.asInstanceOf[Traversal[String, _, _]])
        .filter[String](IndexType.fulltext) {
          case (_, t, _, Right(p))   => searchSrv("Tag", p.getValue)(t)
          case (_, t, _, Left(true)) => t
          case (_, t, _, _)          => t.empty
        }
        .readonly
    )
    .property("namespace", UMapping.string)(_.field.readonly)
    .property("predicate", UMapping.string)(_.field.readonly)
    .property("value", UMapping.string.optional)(_.field.readonly)
    .property("description", UMapping.string.optional)(_.field.readonly)
    .property("text", UMapping.string)(
      _.select(_.displayName)
        .filter[String](IndexType.standard) {
          case (_, tags, authContext, Right(predicate)) => tags.freetags(organisationSrv)(authContext).has(_.predicate, predicate)
          case (_, tags, _, Left(true))                 => tags
          case (_, tags, _, Left(false))                => tags.empty
        }
        .readonly
    )
    .build
}
