package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.CustomFieldSrv
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class CustomFieldCtrl @Inject() (entrypoint: Entrypoint, @Named("with-thehive-schema") db: Database, customFieldSrv: CustomFieldSrv) {

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
