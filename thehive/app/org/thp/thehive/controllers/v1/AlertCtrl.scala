package org.thp.thehive.controllers.v1

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.InputAlert
import org.thp.thehive.models._
import org.thp.thehive.services.{AlertSrv, UserSrv}

@Singleton
class AlertCtrl @Inject()(apiMethod: ApiMethod, db: Database, alertSrv: AlertSrv, userSrv: UserSrv) {

  def create: Action[AnyContent] =
    apiMethod("create alert")
      .extract('alert, FieldsParser[InputAlert])
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val inputAlert: InputAlert = request.body('alert)
          val user                   = userSrv.getOrFail(request.userId)
          val organisation           = userSrv.getOrganisation(user)
          val customFields           = inputAlert.customFieldValue.map(fromInputCustomField)
          val richAlert              = alertSrv.create(request.body('alert), user, organisation, customFields)
          Results.Created(richAlert.toJson)
        }
      }

  def get(alertId: String): Action[AnyContent] =
    apiMethod("get alert")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val richAlert = alertSrv
            .get(alertId)
            .availableFor(request.organisation)
            .richAlert
            .headOption
            .getOrElse(throw NotFoundError(s"alert $alertId not found"))
          if (richAlert.organisation != request.organisation)
            throw NotFoundError(s"alert $alertId not found")
          Results.Ok(richAlert.toJson)
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list alert")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val alerts = alertSrv.initSteps
            .availableFor(request.organisation)
            .richAlert
            .map(_.toJson)
            .toList
          Results.Ok(Json.toJson(alerts))
        }
      }

  def update(alertId: String): Action[AnyContent] =
    apiMethod("update alert")
      .extract('alert, UpdateFieldsParser[InputAlert])
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          alertSrv.update(alertId, request.body('alert))
          Results.NoContent
        }
      }

  def mergeWithCase(alertId: String, caseId: String) = ???
  def markAsRead(alertId: String): Action[AnyContent] =
    apiMethod("mark alert as read")
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          // is imported => Imported
          //  * => Ignored
          alertSrv.update(alertId, "", "")
          Results.NoContent
        }
      }
  def markAsUnread(alertId: String)  = ???
  def createCase(alertId: String)    = ???
  def followAlert(alertId: String)   = ???
  def unfollowAlert(alertId: String) = ???
}
