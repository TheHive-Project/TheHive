package org.thp.thehive.controllers.v1

import io.scalaland.chimney.dsl._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser}
import org.thp.scalligraph.models.{Database, Output}
import org.thp.thehive.models._
import org.thp.thehive.services.CustomFieldSrv
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, Results}

case class OutputCustomFields(name: String, description: String, `type`: String)
object OutputCustomFields {
  def fromCustomField(cf: CustomField): OutputCustomFields =
    cf.into[OutputCustomFields]
      .withFieldComputed(_.`type`, _.`type`.name)
      .transform
  implicit val writes: Writes[OutputCustomFields] = Output[OutputCustomFields]
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
          val outputCustomField  = OutputCustomFields.fromCustomField(createdCustomField)
          Results.Created(Json.toJson(outputCustomField))
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list custom fields")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val customFields = customFieldSrv.steps.toList
            .map(OutputCustomFields.fromCustomField)
          Results.Ok(Json.toJson(customFields))
        }
      }
}
