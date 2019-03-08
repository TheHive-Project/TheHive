package org.thp.thehive.controllers.v0

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v0.InputAlert
import org.thp.thehive.models._
import org.thp.thehive.services.{AlertSrv, CaseTemplateSrv, UserSrv}

@Singleton
class AlertCtrl @Inject()(apiMethod: ApiMethod, db: Database, alertSrv: AlertSrv, caseTemplateSrv: CaseTemplateSrv, userSrv: UserSrv) {

  def create: Action[AnyContent] =
    apiMethod("create alert")
      .extract('alert, FieldsParser[InputAlert])
      .extract('caseTemplate, FieldsParser[String].optional.on("caseTemplate"))
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val caseTemplateName: Option[String] = request.body('caseTemplate)
          val caseTemplate = caseTemplateName
            .map { ct ⇒
              caseTemplateSrv
                .get(ct)
                .availableFor(request.organisation)
                .richCaseTemplate
                .getOrFail()
            }
          val inputAlert: InputAlert = request.body('alert)
          val user                   = userSrv.getOrFail(request.userId)
          val organisation           = userSrv.getOrganisation(user)
          val customFields           = inputAlert.customFieldValue.map(fromInputCustomField).toMap
          val richAlert              = alertSrv.create(request.body('alert), organisation, customFields, caseTemplate)
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
            .toList()
          Results.Ok(Json.toJson(alerts))
        }
      }

  def update(alertId: String): Action[AnyContent] =
    apiMethod("update alert")
      .extract('alert, UpdateFieldsParser[InputAlert])
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (alertSrv.isAvailableFor(alertId)) {
            alertSrv.update(alertId, request.body('alert))
            Results.NoContent
          } else Results.Unauthorized(s"Alert $alertId doesn't exist or permission is insufficient")
        }
      }

  def mergeWithCase(alertId: String, caseId: String) = ???

  def markAsRead(alertId: String): Action[AnyContent] =
    apiMethod("mark alert as read")
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (alertSrv.isAvailableFor(alertId)) {
            alertSrv.markAsRead(alertId)
            Results.NoContent
          } else Results.Unauthorized(s"Alert $alertId doesn't exist or permission is insufficient")
        }
      }

  def markAsUnread(alertId: String): Action[AnyContent] =
    apiMethod("mark alert as unread")
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (alertSrv.isAvailableFor(alertId)) {
            alertSrv.markAsUnread(alertId)
            Results.NoContent
          } else Results.Unauthorized(s"Alert $alertId doesn't exist or permission is insufficient")
        }
      }

  def createCase(alertId: String): Action[AnyContent] =
    apiMethod("create case from alert")
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val alert        = alertSrv.get(alertId).availableFor(request.organisation).richAlert.getOrFail()
          val user         = userSrv.getOrFail(request.userId)
          val organisation = userSrv.getOrganisation(user)
          val richCase     = alertSrv.createCase(alert, Some(user), organisation)
          Results.Created(richCase.toJson)
        }
      }

  def followAlert(alertId: String): Action[AnyContent] =
    apiMethod("follow alert")
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (alertSrv.isAvailableFor(alertId)) {
            alertSrv.followAlert(alertId)
            Results.NoContent
          } else Results.Unauthorized(s"Alert $alertId doesn't exist or permission is insufficient")
        }
      }

  def unfollowAlert(alertId: String): Action[AnyContent] =
    apiMethod("unfollow alert")
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (alertSrv.isAvailableFor(alertId)) {
            alertSrv.unfollowAlert(alertId)
            Results.NoContent
          } else Results.Unauthorized(s"Alert $alertId doesn't exist or permission is insufficient")
        }
      }
}
