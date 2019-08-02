package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.models._
import org.thp.thehive.services.CustomFieldSrv
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class CustomFieldCtrl @Inject()(entryPoint: EntryPoint, db: Database, customFieldSrv: CustomFieldSrv) {
  import CustomFieldConversion._

  def create: Action[AnyContent] =
    entryPoint("create custom field")
      .extract("customField", FieldsParser[CustomField])
      .authTransaction(db) { implicit request => implicit graph =>
        val customField = request.body("customField")
        customFieldSrv
          .create(customField)
          .map(createdCustomField => Results.Created(createdCustomField.toJson))
      }

  def list: Action[AnyContent] =
    entryPoint("list custom fields")
      .authRoTransaction(db) { _ => implicit graph =>
        val customFields = customFieldSrv
          .initSteps
          .map(_.toJson)
          .toList
        Success(Results.Ok(Json.toJson(customFields)))
      }
}
