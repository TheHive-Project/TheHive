package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputObservableType
import org.thp.thehive.models.{ObservableType, Permissions}
import org.thp.thehive.services.ObservableTypeSrv
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class ObservableTypeCtrl @Inject() (
    override val entrypoint: Entrypoint,
    @Named("with-thehive-schema") override val db: Database,
    observableTypeSrv: ObservableTypeSrv,
    @Named("v0") override val queryExecutor: QueryExecutor,
    override val publicData: PublicObservableType
) extends QueryCtrl {
  def get(idOrName: String): Action[AnyContent] =
    entrypoint("get observable type").authRoTransaction(db) { _ => implicit graph =>
      observableTypeSrv
        .get(idOrName)
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
        observableTypeSrv.remove(idOrName).map(_ => Results.NoContent)
      }
}

@Singleton
class PublicObservableType @Inject() (observableTypeSrv: ObservableTypeSrv) extends PublicData {
  override val entityName: String = "ObservableType"
  override val initialQuery: Query =
    Query.init[Traversal.V[ObservableType]]("listObservableType", (graph, _) => observableTypeSrv.startTraversal(graph))
  override val pageQuery: ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[ObservableType], IteratorOutput](
      "page",
      FieldsParser[OutputParam],
      (range, observableTypeSteps, _) => observableTypeSteps.richPage(range.from, range.to, withTotal = true)(identity)
    )
  override val outputQuery: Query = Query.output[ObservableType with Entity]
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, Traversal.V[ObservableType]](
    "getObservableType",
    FieldsParser[IdOrName],
    (param, graph, _) => observableTypeSrv.get(param.idOrName)(graph)
  )
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[ObservableType]
    .property("name", UMapping.string)(_.field.readonly)
    .property("isAttachment", UMapping.boolean)(_.field.readonly)
    .build
}
