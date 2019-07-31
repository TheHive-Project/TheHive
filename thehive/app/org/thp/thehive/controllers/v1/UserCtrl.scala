package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{PropertyUpdater, PublicProperty}
import org.thp.scalligraph.{AuthorizationError, RichOptionTry}
import org.thp.thehive.dto.v1.InputUser
import org.thp.thehive.models._
import org.thp.thehive.services.{OrganisationSrv, ProfileSrv, UserSrv}
import play.api.http.HttpErrorHandler
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext
import scala.util.Failure

@Singleton
class UserCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    userSrv: UserSrv,
    authSrv: AuthSrv,
    organisationSrv: OrganisationSrv,
    profileSrv: ProfileSrv,
    errorHandler: HttpErrorHandler,
    implicit val ec: ExecutionContext
) {

  import UserConversion._

  val publicProperties: Seq[PublicProperty[_, _]] = userProperties(userSrv)

  def current: Action[AnyContent] =
    entryPoint("current user")
      .authTransaction(db) { implicit request => implicit graph =>
        userSrv
          .current
          .richUser(request.organisation)
          .getOrFail()
          .map(user => Results.Ok(user.toJson))
      }

  def create: Action[AnyContent] =
    entryPoint("create user")
      .extract("user", FieldsParser[InputUser])
      .auth { implicit request =>
        val inputUser: InputUser = request.body("user")
        db.tryTransaction { implicit graph =>
            val organisationName = inputUser.organisation.getOrElse(request.organisation)
            for {
              _            <- userSrv.current.organisations(Permissions.manageUser).get(organisationName).existsOrFail()
              organisation <- organisationSrv.getOrFail(organisationName)
              profile      <- profileSrv.getOrFail(inputUser.profile)
              user = userSrv.create(inputUser, organisation, profile)
            } yield user
          }
          .flatMap { user =>
            inputUser
              .password
              .map(password => authSrv.setPassword(user._id, password))
              .flip
              .map(_ => Results.Created(user.toJson))
          }
      }

  def get(userId: String): Action[AnyContent] =
    entryPoint("get user")
      .authTransaction(db) { request => implicit graph =>
        userSrv
          .get(userId)
          .richUser(request.organisation) // FIXME what if user is not in the same org ?
          .getOrFail()
          .map(user => Results.Ok(user.toJson))
      }

//  def list: Action[AnyContent] =
//    entryPoint("list user")
//      .extract("organisation", FieldsParser[String].optional.on("organisation"))
//      .authTransaction(db) { request ⇒ implicit graph ⇒
//          val organisation = request.body("organisation").getOrElse(request.organisation)
//          val users = userSrv.initSteps
//            .richUser(organisation)
//            .map(_.toJson)
//            .toList
//          Results.Ok(Json.toJson(users))
//        }

  def update(userId: String): Action[AnyContent] =
    entryPoint("update user")
      .extract("user", FieldsParser.update("user", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("user")
        userSrv // Authorisation is managed in public properties
          .update(_.get(userId), propertyUpdaters)
          .map(_ => Results.NoContent)
      }

  def setPassword(userId: String): Action[AnyContent] =
    entryPoint("set password")
      .extract("password", FieldsParser[String])
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          _ <- userSrv
            .current
            .organisations(Permissions.manageUser)
            .users
            .get(userId)
            .existsOrFail()
          _ <- authSrv.setPassword(userId, request.body("password"))
        } yield Results.NoContent
      }

  def changePassword(userId: String): Action[AnyContent] =
    entryPoint("change password")
      .extract("password", FieldsParser[String])
      .extract("currentPassword", FieldsParser[String])
      .auth { implicit request =>
        if (userId == request.userId) {
          authSrv
            .changePassword(userId, request.body("currentPassword"), request.body("password"))
            .map(_ => Results.NoContent)
        } else Failure(AuthorizationError(s"You are not authorized to change password of $userId"))
      }

  def getKey(userId: String): Action[AnyContent] =
    entryPoint("get key")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          _ <- userSrv
            .current
            .organisations(Permissions.manageUser)
            .users
            .get(userId)
            .existsOrFail()
          key <- authSrv
            .getKey(userId)
        } yield Results.Ok(key)
      }

  def removeKey(userId: String): Action[AnyContent] =
    entryPoint("remove key")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          _ <- userSrv
            .current
            .organisations(Permissions.manageUser)
            .users
            .get(userId)
            .existsOrFail()
          _ <- authSrv
            .removeKey(userId)
        } yield Results.NoContent
      }

  def renewKey(userId: String): Action[AnyContent] =
    entryPoint("renew key")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          _ <- userSrv
            .current
            .organisations(Permissions.manageUser)
            .users
            .get(userId)
            .existsOrFail()
          key <- authSrv
            .renewKey(userId)
        } yield Results.Ok(key)
      }
}
