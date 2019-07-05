package org.thp.thehive.controllers.v0

import scala.util.Success

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.models._
import org.thp.thehive.services.CustomFieldSrv

@Singleton
class CustomFieldCtrl @Inject()(entryPoint: EntryPoint, db: Database, customFieldSrv: CustomFieldSrv) {
  import CustomFieldConversion._

  def create: Action[AnyContent] =
    entryPoint("create custom field")
      .extract("customField", FieldsParser[CustomField])
      .authTransaction(db) { implicit request => implicit graph =>
        val customField        = request.body("customField")
        val createdCustomField = customFieldSrv.create(customField)
        Success(Results.Created(createdCustomField.toJson))
      }

  def list: Action[AnyContent] =
    entryPoint("list custom fields")
      .authTransaction(db) { _ => implicit graph =>
        val customFields = customFieldSrv
          .initSteps
          .map(_.toJson)
          .toList
        Success(Results.Ok(Json.toJson(customFields)))
      }
}
