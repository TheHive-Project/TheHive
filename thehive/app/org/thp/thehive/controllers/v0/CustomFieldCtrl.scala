package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.thehive.dto.v0.InputCustomField
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.CustomFieldSrv
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Success, Try}

@Singleton
class CustomFieldCtrl @Inject()(entryPoint: EntryPoint, db: Database, customFieldSrv: CustomFieldSrv) {
  import CustomFieldConversion._

  val permissions: Set[Permission] = Set(Permissions.manageCustomField)

  def create: Action[AnyContent] =
    entryPoint("create custom field")
      .extract("customField", FieldsParser[InputCustomField].on("value"))
      .authPermittedTransaction(db, permissions) { implicit request => implicit graph =>
        val customField: InputCustomField = request.body("customField")
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

  def delete(id: String): Action[AnyContent] =
    entryPoint("delete custom field")
      .authPermittedTransaction(db, permissions) { implicit request => implicit graph =>
        Try(
          customFieldSrv
            .get(id)
            .remove()
        ).map(_ => Results.NoContent)
      }

  def update(id: String): Action[AnyContent] =
    entryPoint("update custom field")
      .extract("customField", FieldsParser.update("customField", customFieldProperties).on("value"))
      .authPermittedTransaction(db, permissions) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("customField")

        for {
          updated <- customFieldSrv.update(customFieldSrv.get(id), propertyUpdaters)
          cf      <- updated._1.getOrFail()
        } yield Results.Ok(cf.toJson)
      }
}
