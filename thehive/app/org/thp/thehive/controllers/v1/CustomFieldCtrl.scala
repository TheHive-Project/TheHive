package org.thp.thehive.controllers.v1

import scala.util.Success

import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.CustomFieldSrv

@Singleton
class CustomFieldCtrl @Inject() (entrypoint: Entrypoint, db: Database, customFieldSrv: CustomFieldSrv) {

  def create: Action[AnyContent] =
    entrypoint("create custom field")
      .extract("customField", FieldsParser[CustomField])
      .authTransaction(db) { implicit request => implicit graph =>
        val customField = request.body("customField")
        customFieldSrv
          .create(customField)
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
}
