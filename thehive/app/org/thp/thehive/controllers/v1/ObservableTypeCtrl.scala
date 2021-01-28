package org.thp.thehive.controllers.v1

import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputObservableType
import org.thp.thehive.models.{ObservableType, Permissions}
import org.thp.thehive.services.ObservableTypeSrv
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}

@Singleton
class ObservableTypeCtrl @Inject() (
    val entrypoint: Entrypoint,
    db: Database,
    observableTypeSrv: ObservableTypeSrv
) extends QueryableCtrl {
  override val entityName: String = "ObservableType"
  override val initialQuery: Query =
    Query.init[Traversal.V[ObservableType]]("listObservableType", (graph, _) => observableTypeSrv.startTraversal(graph))
  override val pageQuery: ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[ObservableType], IteratorOutput](
      "page",
      (range, observableTypeSteps, _) => observableTypeSteps.richPage(range.from, range.to, withTotal = true)(identity)
    )
  override val outputQuery: Query = Query.output[ObservableType with Entity]
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[ObservableType]](
    "getObservableType",
    (idOrName, graph, _) => observableTypeSrv.get(idOrName)(graph)
  )
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[ObservableType]
    .property("name", UMapping.string)(_.field.readonly)
    .property("isAttachment", UMapping.boolean)(_.field.readonly)
    .build

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
