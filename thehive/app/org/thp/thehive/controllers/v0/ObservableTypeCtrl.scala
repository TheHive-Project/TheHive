package org.thp.thehive.controllers.v0

import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, IndexType, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal, TraversalOps}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputObservableType
import org.thp.thehive.models.{ObservableType, Permissions}
import org.thp.thehive.services.{ObservableTypeSrv, SearchSrv}
import play.api.mvc.{Action, AnyContent, Results}

class ObservableTypeCtrl(
    override val entrypoint: Entrypoint,
    override val db: Database,
    observableTypeSrv: ObservableTypeSrv,
    override val queryExecutor: QueryExecutor,
    override val publicData: PublicObservableType
) extends QueryCtrl
    with TraversalOps {
  def get(idOrName: String): Action[AnyContent] =
    entrypoint("get observable type").authRoTransaction(db) { _ => implicit graph =>
      observableTypeSrv
        .get(EntityIdOrName(idOrName))
        .getOrFail("Observable")
        .map(ot => Results.Ok(ot.toJson))
    }

  def create: Action[AnyContent] =
    entrypoint("create observable type")
      .extract("observableType", FieldsParser[InputObservableType])
      .authPermittedTransaction(db, Permissions.manageObservableTemplate) { implicit request => implicit graph =>
        val inputObservableType: InputObservableType = request.body("observableType")
        observableTypeSrv
          .create(inputObservableType.toObservableType)
          .map(observableType => Results.Created(observableType.toJson))
      }

  def delete(idOrName: String): Action[AnyContent] =
    entrypoint("delete observable type")
      .authPermittedTransaction(db, Permissions.manageObservableTemplate) { _ => implicit graph =>
        observableTypeSrv.remove(EntityIdOrName(idOrName)).map(_ => Results.NoContent)
      }
}

class PublicObservableType(observableTypeSrv: ObservableTypeSrv, searchSrv: SearchSrv) extends PublicData with TraversalOps {
  override val entityName: String = "ObservableType"
  override val initialQuery: Query =
    Query.init[Traversal.V[ObservableType]]("listObservableType", (graph, _) => observableTypeSrv.startTraversal(graph))
  override def pageQuery(limitedCountThreshold: Long): ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[ObservableType], IteratorOutput](
      "page",
      (range, observableTypeSteps, _) => observableTypeSteps.richPage(range.from, range.to, withTotal = true, limitedCountThreshold)(identity)
    )
  override val outputQuery: Query = Query.output[ObservableType with Entity]
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[ObservableType]](
    "getObservableType",
    (idOrName, graph, _) => observableTypeSrv.get(idOrName)(graph)
  )
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[ObservableType]
    .property("keyword", UMapping.string)(
      _.select(_.empty.asInstanceOf[Traversal[String, _, _]])
        .filter[String](IndexType.fulltext) {
          case (_, t, _, Right(p))   => searchSrv("ObservableType", p.getValue)(t)
          case (_, t, _, Left(true)) => t
          case (_, t, _, _)          => t.empty
        }
        .readonly
    )
    .property("name", UMapping.string)(_.field.readonly)
    .property("isAttachment", UMapping.boolean)(_.field.readonly)
    .build
}
