package org.thp.thehive.controllers.v1

import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, IndexType, UMapping}
import org.thp.scalligraph.query.{ParamQuery, PublicProperties, PublicPropertyListBuilder, Query}
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputCustomField
import org.thp.thehive.models._
import org.thp.thehive.services.{CustomFieldSrv, SearchSrv, TheHiveOpsNoDeps}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

class CustomFieldCtrl(entrypoint: Entrypoint, db: Database, customFieldSrv: CustomFieldSrv, searchSrv: SearchSrv)
    extends QueryableCtrl
    with TheHiveOpsNoDeps {

  override val entityName: String  = "CustomField"
  override val initialQuery: Query = Query.init[Traversal.V[CustomField]]("listCustomField", (graph, _) => customFieldSrv.startTraversal(graph))
  override def pageQuery(limitedCountThreshold: Long): ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[CustomField], IteratorOutput](
      "page",
      {
        case (OutputParam(from, to, _), customFieldSteps, _) =>
          customFieldSteps.page(from, to, withTotal = true, limitedCountThreshold)
      }
    )
  override val outputQuery: Query = Query.output[CustomField with Entity]
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[CustomField]](
    "getCustomField",
    (idOrName, graph, _) => customFieldSrv.get(idOrName)(graph)
  )
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[CustomField]
    .property("keyword", UMapping.string)(
      _.select(_.empty.asInstanceOf[Traversal[String, _, _]])
        .filter[String](IndexType.fulltext) {
          case (_, t, _, Right(p))   => searchSrv("CustomField", p.getValue)(t)
          case (_, t, _, Left(true)) => t
          case (_, t, _, _)          => t.empty
        }
        .readonly
    )
    .property("name", UMapping.string)(_.rename("displayName").updatable)
    .property("description", UMapping.string)(_.field.updatable)
    .property("reference", UMapping.string)(_.rename("name").readonly)
    .property("mandatory", UMapping.boolean)(_.field.updatable)
    .property("type", UMapping.string)(_.field.updatable)
    .property("options", UMapping.json.sequence)(_.field.updatable)
    .build

  def create: Action[AnyContent] =
    entrypoint("create custom field")
      .extract("customField", FieldsParser[InputCustomField])
      .authTransaction(db) { implicit request => implicit graph =>
        val customField = request.body("customField")
        customFieldSrv
          .create(customField.toCustomField)
          .map(createdCustomField => Results.Created(createdCustomField.toJson))
      }

  def list: Action[AnyContent] =
    entrypoint("list custom fields")
      .authRoTransaction(db) { _ => implicit graph =>
        val customFields = customFieldSrv
          .startTraversal
          .toSeq
        Success(Results.Ok(customFields.toJson))
      }
}
