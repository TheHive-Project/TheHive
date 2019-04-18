package org.thp.thehive.controllers.v1

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

import play.api.http.HttpErrorHandler
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.AuthorizationError
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.InputUser
import org.thp.thehive.models._
import org.thp.thehive.services.{OrganisationSrv, ProfileSrv, UserSrv}

@Singleton
class UserCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    userSrv: UserSrv,
    authSrv: AuthSrv,
    organisationSrv: OrganisationSrv,
    profileSrv: ProfileSrv,
    errorHandler: HttpErrorHandler,
    implicit val ec: ExecutionContext)
    extends UserConversion {

  def current: Action[AnyContent] =
    entryPoint("current user").authenticated { implicit request ⇒
      db.tryTransaction { implicit graph ⇒
        userSrv
          .get(request.userId)
          .richUser(request.organisation)
          .getOrFail()
          .map(user ⇒ Results.Ok(user.toJson))
      }
    }

  def create: Action[AnyContent] =
    entryPoint("create user")
      .extract('user, FieldsParser[InputUser])
      .authenticated { implicit request ⇒
        val inputUser: InputUser = request.body('user)
        db.tryTransaction { implicit graph ⇒
            val organisationName = inputUser.organisation.getOrElse(request.organisation)
            for {
              _            ← userSrv.current.organisations(Permissions.manageUser).get(organisationName).existsOrFail()
              organisation ← organisationSrv.getOrFail(organisationName)
              profile      ← profileSrv.getOrFail(inputUser.profile)
              user = userSrv.create(inputUser, organisation, profile)
            } yield user
          }
          .map { user ⇒
            inputUser.password.foreach(password ⇒ authSrv.setPassword(user._id, password))
            Results.Created(user.toJson)
          }
      }

  def get(userId: String): Action[AnyContent] =
    entryPoint("get user").authenticated { request ⇒
      db.tryTransaction { implicit graph ⇒
        userSrv
          .get(userId)
          .richUser(request.organisation) // FIXME what if user is not in the same org ?
          .getOrFail()
          .map(user ⇒ Results.Ok(user.toJson))
      }
    }

//  def list: Action[AnyContent] =
//    entryPoint("list user")
//      .extract('organisation, FieldsParser[String].optional.on("organisation"))
//      .authenticated { request ⇒
//        db.transaction { implicit graph ⇒
//          val organisation = request.body('organisation).getOrElse(request.organisation)
//          val users = userSrv.initSteps
//            .richUser(organisation)
//            .map(_.toJson)
//            .toList
//          Results.Ok(Json.toJson(users))
//        }
//      }

  def update(userId: String): Action[AnyContent] =
    entryPoint("update user")
      .extract('user, UpdateFieldsParser[InputUser])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          userSrv.update(userId, request.body('user))
          Success(Results.NoContent)
        }
      }

  def setPassword(userId: String): Action[AnyContent] =
    entryPoint("set password")
      .extract('password, FieldsParser[String])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          for {
            _ ← userSrv.current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .existsOrFail()
            _ ← authSrv.setPassword(userId, request.body('password))
          } yield Results.NoContent
        }
      }

  def changePassword(userId: String): Action[AnyContent] =
    entryPoint("change password")
      .extract('password, FieldsParser[String])
      .extract('currentPassword, FieldsParser[String])
      .authenticated { implicit request ⇒
        if (userId == request.userId) {
          db.tryTransaction { _ ⇒
            authSrv
              .changePassword(userId, request.body('currentPassword), request.body('password))
              .map(_ ⇒ Results.NoContent)
          }
        } else Failure(AuthorizationError(s"You are not authorized to change password of $userId"))
      }

  def getKey(userId: String): Action[AnyContent] =
    entryPoint("get key")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          for {
            _ ← userSrv.current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .existsOrFail()
            key ← authSrv
              .getKey(userId)
          } yield Results.Ok(key)
        }
      }

  def removeKey(userId: String): Action[AnyContent] =
    entryPoint("remove key")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          for {
            _ ← userSrv.current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .existsOrFail()
            _ ← authSrv
              .removeKey(userId)
          } yield Results.NoContent
        }
      }

  def renewKey(userId: String): Action[AnyContent] =
    entryPoint("renew key")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          for {
            _ ← userSrv.current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .existsOrFail()
            key ← authSrv
              .renewKey(userId)
          } yield Results.Ok(key)
        }
      }
}
