package org.thp.thehive.controllers.v0

import scala.concurrent.ExecutionContext

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v0.InputUser
import org.thp.thehive.models._
import org.thp.thehive.services.{OrganisationSrv, UserSrv}

@Singleton
class UserCtrl @Inject()(
    apiMethod: ApiMethod,
    db: Database,
    userSrv: UserSrv,
    authSrv: AuthSrv,
    organisationSrv: OrganisationSrv,
    implicit val ec: ExecutionContext) {

  def current: Action[AnyContent] =
    apiMethod("current user")
      .requires() { implicit request ⇒
        db.transaction { implicit graph ⇒
          userSrv
            .get(request.userId)
            .richUser
            .headOption()
            .fold(Results.Unauthorized(Json.obj("type" → "AuthenticationError", "message" → "You are not authenticated"))) { user ⇒
              Results.Ok(user.toJson)
            }
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
      .requires(Permissions.read) { _ ⇒
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
      .requires(Permissions.read) { _ ⇒
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
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (userSrv.isAvailableFor(userId, request.organisation))
            authSrv
              .setPassword(userId, request.body('password))
              .map(_ ⇒ Results.NoContent)
              .get
          else
            Results.Unauthorized(s"User $userId doesn't exist or permission is insufficient")
        }
      }

  def changePassword(userId: String): Action[AnyContent] =
    apiMethod("change password")
      .extract('password, FieldsParser[String])
      .extract('currentPassword, FieldsParser[String])
      .requires() { implicit request ⇒
        if (userId == request.userId) {
          authSrv
            .changePassword(userId, request.body('currentPassword), request.body('password))
            .map(_ ⇒ Results.NoContent)
            .get
        } else Results.Unauthorized(s"You are not authorized to change password of $userId")
      }

  def getKey(userId: String): Action[AnyContent] =
    apiMethod("get key")
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (userSrv.isAvailableFor(userId, request.organisation))
            authSrv
              .getKey(userId)
              .map(Results.Ok(_))
              .get
          else
            Results.Unauthorized(s"User $userId doesn't exist or permission is insufficient")
        }
      }

  def removeKey(userId: String): Action[AnyContent] =
    apiMethod("remove key")
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (userSrv.isAvailableFor(userId, request.organisation))
            authSrv
              .removeKey(userId)
              .map(_ ⇒ Results.NoContent)
              .get
          else
            Results.Unauthorized(s"User $userId doesn't exist or permission is insufficient")
        }
      }

  def renewKey(userId: String): Action[AnyContent] =
    apiMethod("renew key")
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (userSrv.isAvailableFor(userId, request.organisation))
            authSrv
              .renewKey(userId)
              .map(Results.Ok(_))
              .get
          else
            Results.Unauthorized(s"User $userId doesn't exist or permission is insufficient")
        }
      }

  def createInitialUser: Action[AnyContent] =
    apiMethod("create initial user")
      .requires() { implicit request ⇒
        val user = db.transaction { implicit graph ⇒
          val defaultOrganisation = organisationSrv.create(Organisation("default"))
          userSrv.create(User("admin", "admin", None, Permissions.permissions, UserStatus.ok, None), defaultOrganisation)
        }
        authSrv
          .setPassword(user._id, "admin")
          .map(_ ⇒ Results.Ok)
          .get
      }
}
