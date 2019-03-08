package org.thp.thehive.controllers.v0

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.models._
import org.thp.thehive.services.CustomFieldSrv

@Singleton
class CustomFieldCtrl @Inject()(apiMethod: ApiMethod, db: Database, customFieldSrv: CustomFieldSrv) {

  def create: Action[AnyContent] =
    apiMethod("create custom field")
      .extract('customField, FieldsParser[CustomField])
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val customField        = request.body('customField)
          val createdCustomField = customFieldSrv.create(customField)
          Results.Created(createdCustomField.toJson)
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list custom fields")
      .requires(Permissions.read) { _ ⇒
        db.transaction { implicit graph ⇒
          val customFields = customFieldSrv.initSteps
            .map(_.toJson)
            .toList()
          Results.Ok(Json.toJson(customFields))
        }
      }
}
