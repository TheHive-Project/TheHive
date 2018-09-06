package org.thp.thehive.controllers.v1

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import io.scalaland.chimney.dsl._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.{InputUser, OutputUser}
import org.thp.thehive.models._
import org.thp.thehive.services.UserSrv

object UserXfrm {
  def fromInput(inputUser: InputUser): User =
    inputUser
      .into[User]
      .withFieldComputed(_.id, _.login)
      .withFieldConst(_.apikey, None)
      .withFieldConst(_.password, None)
      .withFieldConst(_.status, UserStatus.ok)
      .withFieldComputed(_.permissions, _.permissions.flatMap(Permissions.withName)) // FIXME unkown permissions are ignored
      .transform

  def toOutput(user: User): OutputUser =
    user
      .into[OutputUser]
      .withFieldComputed(_.permissions, _.permissions.map(_.name).toSet)
      .transform

}

@Singleton
class UserCtrl @Inject()(apiMethod: ApiMethod, db: Database, userSrv: UserSrv, authSrv: AuthSrv) {

  def create: Action[AnyContent] =
    apiMethod("create user")
      .extract('user, FieldsParser[InputUser])
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val inputUser   = request.body('user)
          val createdUser = userSrv.create(UserXfrm.fromInput(inputUser))
          inputUser.password.foreach(password ⇒ authSrv.setPassword(createdUser._id, password))
          val outputUser = UserXfrm.toOutput(createdUser)
          Results.Created(Json.toJson(outputUser))
        }
      }

  def get(userId: String): Action[AnyContent] =
    apiMethod("get user")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val user = userSrv
            .getOrFail(userId)
          val outputUser = UserXfrm.toOutput(user)
          Results.Ok(Json.toJson(outputUser))
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list user")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val users = userSrv.steps.toList
            .map(UserXfrm.toOutput)

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
}
