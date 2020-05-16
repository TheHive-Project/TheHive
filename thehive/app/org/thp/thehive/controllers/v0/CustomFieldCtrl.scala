package org.thp.thehive.controllers.v0

import scala.util.Success

import play.api.libs.json.{JsNumber, JsObject}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputCustomField
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.CustomFieldSrv

@Singleton
class CustomFieldCtrl @Inject() (entrypoint: Entrypoint, db: Database, properties: Properties, customFieldSrv: CustomFieldSrv) extends AuditRenderer {

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
          .initSteps
          .toList
        Success(Results.Ok(customFields.toJson))
      }

  def get(id: String): Action[AnyContent] =
    entrypoint("get custom field")
      .authRoTransaction(db) { _ => implicit graph =>
        customFieldSrv.get(id).getOrFail("CustomField").map(cf => Results.Ok(cf.toJson))
      }

  def delete(id: String): Action[AnyContent] =
    entrypoint("delete custom field")
      .extract("force", FieldsParser.boolean.optional.on("force"))
      .authPermittedTransaction(db, Permissions.manageCustomField) { implicit request => implicit graph =>
        val force = request.body("force").getOrElse(false)
        for {
          cf <- customFieldSrv.getOrFail(id)
          _  <- customFieldSrv.delete(cf, force)
        } yield Results.NoContent
      }

  def update(id: String): Action[AnyContent] =
    entrypoint("update custom field")
      .extract("customField", FieldsParser.update("customField", properties.customField))
      .authPermittedTransaction(db, Permissions.manageCustomField) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("customField")

        for {
          updated <- customFieldSrv.update(customFieldSrv.get(id), propertyUpdaters)
          cf      <- updated._1.getOrFail("CustomField")
        } yield Results.Ok(cf.toJson)
      }

  def useCount(id: String): Action[AnyContent] =
    entrypoint("get use count of custom field")
      .authPermittedTransaction(db, Permissions.manageCustomField) { _ => implicit graph =>
        customFieldSrv.getOrFail(id).map(customFieldSrv.useCount).map { countMap =>
          val total = countMap.valuesIterator.sum
          val countStats = JsObject(countMap.map {
            case (k, v) => fromObjectType(k) -> JsNumber(v)
          })
          Results.Ok(countStats + ("total" -> JsNumber(total)))
        }
      }
}
