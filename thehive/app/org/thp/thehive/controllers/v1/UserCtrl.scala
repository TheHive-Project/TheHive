package org.thp.thehive.controllers.v1

import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.MultiAuthSrv
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.{AuthenticationError, NotFoundError}
import org.thp.thehive.dto.v1.InputUser
import org.thp.thehive.models._
import org.thp.thehive.services.{OrganisationSrv, UserSrv}

@Singleton
class UserCtrl @Inject()(
    apiMethod: ApiMethod,
    db: Database,
    userSrv: UserSrv,
    authSrv: MultiAuthSrv,
    organisationSrv: OrganisationSrv,
    implicit val ec: ExecutionContext) {

  def current: Action[AnyContent] =
    apiMethod("current user")
      .requires() { implicit request ⇒
        db.transaction { implicit graph ⇒
          val user = userSrv
            .get(request.userId)
            .richUser
            .headOption()
            .getOrElse(throw AuthenticationError("Authentication failure"))
          Results.Ok(user.toJson)
        }
      }
  def create: Action[AnyContent] =
    apiMethod("create user")
      .extract('user, FieldsParser[InputUser])
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val inputUser: InputUser = request.body('user)
          val organisationName     = inputUser.organisation.getOrElse(request.organisation)
          val organisation         = organisationSrv.getOrFail(organisationName)
          // TODO check if the requester can create a new user in that organisation
          val richUser = userSrv.create(inputUser, organisation)
          inputUser.password.foreach(password ⇒ authSrv.setPassword(richUser._id, password))
          Results.Created(richUser.toJson)
        }
      }

  def get(userId: String): Action[AnyContent] =
    apiMethod("get user")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val user = userSrv
            .get(userId)
            .richUser
            .headOption()
            .getOrElse(throw NotFoundError(s"user $userId not found"))
          Results.Ok(user.toJson)
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list user")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val users = userSrv.initSteps.richUser
            .map(_.toJson)
            .toList
          Results.Ok(Json.toJson(users))
        }
      }

  def update(userId: String): Action[AnyContent] =
    apiMethod("update user")
      .extract('user, UpdateFieldsParser[InputUser])
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          userSrv.update(userId, request.body('user))
          Results.NoContent
        }
      }

  def setPassword(userId: String): Action[AnyContent] =
    apiMethod("set password")
      .extract('password, FieldsParser[String])
      .requires(Permissions.admin)
      .async { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (userSrv.isAvailableFor(userId, request.organisation))
            authSrv
              .setPassword(userId, request.body('password))
              .map(_ ⇒ Results.NoContent)
          else
            Future.successful(Results.Unauthorized(s"User $userId doesn't exist or permission is insufficient"))
        }
      }

  def changePassword(userId: String): Action[AnyContent] =
    apiMethod("change password")
      .extract('password, FieldsParser[String])
      .extract('currentPassword, FieldsParser[String])
      .requires()
      .async { implicit request ⇒
        if (userId == request.userId) {
          db.transaction { implicit graph ⇒
            authSrv
              .changePassword(userId, request.body('currentPassword), request.body('password))
              .map(_ ⇒ Results.NoContent)
          }
        } else Future.successful(Results.Unauthorized(s"You are not authorized to change password of $userId"))
      }

  def getKey(userId: String): Action[AnyContent] =
    apiMethod("get key")
      .requires(Permissions.admin)
      .async { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (userSrv.isAvailableFor(userId, request.organisation))
            authSrv.getKey(userId).map(Results.Ok(_))
          else
            Future.successful(Results.Unauthorized(s"User $userId doesn't exist or permission is insufficient"))
        }
      }

  def removeKey(userId: String): Action[AnyContent] =
    apiMethod("remove key")
      .requires(Permissions.admin)
      .async { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (userSrv.isAvailableFor(userId, request.organisation))
            authSrv.removeKey(userId).map(_ ⇒ Results.NoContent)
          else
            Future.successful(Results.Unauthorized(s"User $userId doesn't exist or permission is insufficient"))
        }
      }

  def renewKey(userId: String): Action[AnyContent] =
    apiMethod("renew key")
      .requires(Permissions.admin)
      .async { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (userSrv.isAvailableFor(userId, request.organisation))
            authSrv.renewKey(userId).map(Results.Ok(_))
          else
            Future.successful(Results.Unauthorized(s"User $userId doesn't exist or permission is insufficient"))
        }
      }
}
