package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputCustomField
import org.thp.thehive.models.{CustomField, Permissions}
import org.thp.thehive.services.CustomFieldSrv
import play.api.libs.json.{JsNumber, JsObject}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class CustomFieldCtrl @Inject() (
    override val entrypoint: Entrypoint,
    @Named("with-thehive-schema") override val db: Database,
    customFieldSrv: CustomFieldSrv,
    override val publicData: PublicCustomField,
    @Named("v0") override val queryExecutor: QueryExecutor
) extends QueryCtrl
    with AuditRenderer {
  def create: Action[AnyContent] =
    entrypoint("create custom field")
      .extract("customField", FieldsParser[InputCustomField])
      .authPermittedTransaction(db, Permissions.manageCustomField) { implicit request => implicit graph =>
        val customField: InputCustomField = request.body("customField")
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

  def get(id: String): Action[AnyContent] =
    entrypoint("get custom field")
      .authRoTransaction(db) { _ => implicit graph =>
        customFieldSrv.get(EntityIdOrName(id)).getOrFail("CustomField").map(cf => Results.Ok(cf.toJson))
      }

  def delete(id: String): Action[AnyContent] =
    entrypoint("delete custom field")
      .extract("force", FieldsParser.boolean.optional.on("force"))
      .authPermittedTransaction(db, Permissions.manageCustomField) { implicit request => implicit graph =>
        val force = request.body("force").getOrElse(false)
        for {
          cf <- customFieldSrv.getOrFail(EntityIdOrName(id))
          _  <- customFieldSrv.delete(cf, force)
        } yield Results.NoContent
      }

  def update(id: String): Action[AnyContent] =
    entrypoint("update custom field")
      .extract("customField", FieldsParser.update("customField", publicData.publicProperties))
      .authPermittedTransaction(db, Permissions.manageCustomField) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("customField")

        for {
          updated <- customFieldSrv.update(customFieldSrv.get(EntityIdOrName(id)), propertyUpdaters)
          cf      <- updated._1.getOrFail("CustomField")
        } yield Results.Ok(cf.toJson)
      }

  def useCount(id: String): Action[AnyContent] =
    entrypoint("get use count of custom field")
      .authPermittedTransaction(db, Permissions.manageCustomField) { _ => implicit graph =>
        customFieldSrv.getOrFail(EntityIdOrName(id)).map(customFieldSrv.useCount).map { countMap =>
          val total = countMap.valuesIterator.sum
          val countStats = JsObject(countMap.map {
            case (k, v) => fromObjectType(k) -> JsNumber(v)
          })
          Results.Ok(countStats + ("total" -> JsNumber(total)))
        }
      }
}

@Singleton
class PublicCustomField @Inject() (customFieldSrv: CustomFieldSrv) extends PublicData {
  override val entityName: String  = "CustomField"
  override val initialQuery: Query = Query.init[Traversal.V[CustomField]]("listCustomField", (graph, _) => customFieldSrv.startTraversal(graph))
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[CustomField], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    {
      case (OutputParam(from, to, _, _), customFieldSteps, _) =>
        customFieldSteps.page(from, to, withTotal = true)
    }
  )
  override val outputQuery: Query = Query.output[CustomField with Entity]
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[CustomField]](
    "getCustomField",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, _) => customFieldSrv.get(idOrName)(graph)
  )
  override val publicProperties: PublicProperties =
    PublicPropertyListBuilder[CustomField]
      .property("name", UMapping.string)(_.rename("displayName").updatable)
      .property("description", UMapping.string)(_.field.updatable)
      .property("reference", UMapping.string)(_.rename("name").readonly)
      .property("mandatory", UMapping.boolean)(_.field.updatable)
      .property("type", UMapping.string)(_.field.readonly)
      .property("options", UMapping.json.sequence)(_.field.updatable)
      .build
}
