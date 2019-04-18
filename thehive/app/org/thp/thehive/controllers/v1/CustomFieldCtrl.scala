package org.thp.thehive.controllers.v1

import scala.util.Success

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.models._
import org.thp.thehive.services.CustomFieldSrv

@Singleton
class CustomFieldCtrl @Inject()(entryPoint: EntryPoint, db: Database, customFieldSrv: CustomFieldSrv) extends CustomFieldConversion {

  def create: Action[AnyContent] =
    entryPoint("create custom field")
      .extract('customField, FieldsParser[CustomField])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val customField        = request.body('customField)
          val createdCustomField = customFieldSrv.create(customField)
          Success(Results.Created(createdCustomField.toJson))
        }
      }

  def list: Action[AnyContent] =
    entryPoint("list custom fields")
      .authenticated { _ ⇒
        db.tryTransaction { implicit graph ⇒
          val customFields = customFieldSrv.initSteps
            .map(_.toJson)
            .toList()
          Success(Results.Ok(Json.toJson(customFields)))
        }
      }
}
