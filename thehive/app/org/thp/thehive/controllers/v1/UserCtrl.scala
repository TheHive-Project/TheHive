package org.thp.thehive.controllers.v1

import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, Results}

import io.scalaland.chimney.dsl._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.{AuthSrv, Permission}
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser, UpdateFieldsParser, WithParser}
import org.thp.scalligraph.models.{Database, Output}
import org.thp.thehive.models._
import org.thp.thehive.services.UserSrv

case class InputUser(login: String, name: String, @WithParser(Permissions.parser.sequence) permissions: Seq[Permission], password: Option[String]) {
  def toUser: User =
    this
      .into[User]
      .withFieldComputed(_.id, _.login)
      .withFieldConst(_.apikey, None)
      .withFieldConst(_.password, None)
      .withFieldConst(_.status, UserStatus.ok)
      .transform
}

case class OutputUser(login: String, name: String, @WithParser(Permissions.parser.sequence) permissions: Seq[Permission])

object OutputUser {
  def fromUser(user: User): OutputUser    = user.into[OutputUser].transform
  implicit val writes: Writes[OutputUser] = Output[OutputUser]
}

@Singleton
class UserCtrl @Inject()(apiMethod: ApiMethod, db: Database, userSrv: UserSrv, authSrv: AuthSrv) {

  def create: Action[AnyContent] =
    apiMethod("create user")
      .extract('user, FieldsParser[InputUser])
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val inputUser = request.body('user)
          val createdUser = userSrv.create(inputUser.toUser)
          inputUser.password.foreach(password ⇒ authSrv.setPassword(createdUser._id, password))
          val outputUser = OutputUser.fromUser(createdUser)
          Results.Created(Json.toJson(outputUser))
        }
      }

  def get(userId: String): Action[AnyContent] =
    apiMethod("get user")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val user = userSrv
            .getOrFail(userId)
          val outputUser = OutputUser.fromUser(user)
          Results.Ok(Json.toJson(outputUser))
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list user")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val users = userSrv.steps.toList
            .map(OutputUser.fromUser)

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
