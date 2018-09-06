package org.thp.thehive.controllers.v1

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import io.scalaland.chimney.dsl._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.{InputCustomFieldValue, OutputCustomField, OutputCustomFieldValue}
import org.thp.thehive.models._
import org.thp.thehive.services.CustomFieldSrv

object CustomFieldXfrm {
  def fromInput(inputCustomFieldValue: InputCustomFieldValue): (String, Any) = inputCustomFieldValue.name → inputCustomFieldValue.value

  def toOutput(customFieldValue: CustomFieldValue): OutputCustomFieldValue =
    customFieldValue
      .into[OutputCustomFieldValue]
      .withFieldComputed(_.value, _.value.toString)
      .transform

  def toOutput(customField: CustomField): OutputCustomField =
    customField
      .into[OutputCustomField]
      .withFieldComputed(_.`type`, _.`type`.name)
      .transform

}

@Singleton
class CustomFieldCtrl @Inject()(apiMethod: ApiMethod, db: Database, customFieldSrv: CustomFieldSrv) {

  def create: Action[AnyContent] =
    apiMethod("create custom field")
      .extract('customField, FieldsParser[CustomField])
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val customField        = request.body('customField)
          val createdCustomField = customFieldSrv.create(customField)
          val outputCustomField  = CustomFieldXfrm.toOutput(createdCustomField)
          Results.Created(Json.toJson(outputCustomField))
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list custom fields")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val customFields = customFieldSrv.steps.toList
            .map(CustomFieldXfrm.toOutput)
          Results.Ok(Json.toJson(customFields))
        }
      }
}
