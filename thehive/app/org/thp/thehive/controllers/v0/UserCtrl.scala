package org.thp.thehive.controllers.v0

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{PropertyUpdater, Query}
import org.thp.scalligraph.{AuthorizationError, RichOptionTry}
import org.thp.thehive.dto.v0.InputUser
import org.thp.thehive.models._
import org.thp.thehive.services.{OrganisationSrv, ProfileSrv, UserSrv}

@Singleton
class UserCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    userSrv: UserSrv,
    profileSrv: ProfileSrv,
    authSrv: AuthSrv,
    organisationSrv: OrganisationSrv,
    implicit val ec: ExecutionContext,
    val queryExecutor: TheHiveQueryExecutor
) extends UserConversion
    with QueryCtrl {

  def current: Action[AnyContent] =
    entryPoint("current user")
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        userSrv
          .get(request.userId)
          .richUser(request.organisation)
          .getOrFail()
          .map(user ⇒ Results.Ok(user.toJson))
      }

  def create: Action[AnyContent] =
    entryPoint("create user")
      .extract('user, FieldsParser[InputUser])
      .auth { implicit request ⇒
        val inputUser: InputUser = request.body('user)
        db.tryTransaction { implicit graph ⇒
            val organisationName = inputUser.organisation.getOrElse(request.organisation)
            for {
              _            ← userSrv.current.organisations(Permissions.manageUser).get(organisationName).existsOrFail()
              organisation ← organisationSrv.getOrFail(organisationName)
              profile ← if (inputUser.roles.contains("admin")) profileSrv.getOrFail("admin")
              else if (inputUser.roles.contains("write")) profileSrv.getOrFail("analyst")
              else if (inputUser.roles.contains("read")) profileSrv.getOrFail("read-only")
              else ???
              user = userSrv.create(inputUser, organisation, profile)
            } yield user
          }
          .flatMap { user ⇒
            inputUser
              .password
              .map(password ⇒ authSrv.setPassword(user._id, password))
              .flip
              .map(_ ⇒ Results.Created(user.toJson))
          }
      }

  def get(userId: String): Action[AnyContent] =
    entryPoint("get user")
      .authTransaction(db) { request ⇒ implicit graph ⇒
        userSrv
          .get(userId)
          .richUser(request.organisation) // FIXME what if user is not in the same org ?
          .getOrFail()
          .map { user ⇒
            Results.Ok(user.toJson)
          }
      }

  def update(userId: String): Action[AnyContent] =
    entryPoint("update user")
      .extract('user, FieldsParser.update("user", userProperties(userSrv)))
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        val propertyUpdaters: Seq[PropertyUpdater] = request.body('user)
        userSrv // Authorisation is managed in public properties
          .update(_.get(userId), propertyUpdaters)
          .flatMap { case (user, _) ⇒ user.richUser(request.organisation).getOrFail() }
          .map(user ⇒ Results.Ok(user.toJson))
      }

  def setPassword(userId: String): Action[AnyContent] =
    entryPoint("set password")
      .extract('password, FieldsParser[String].on("password"))
      .auth { implicit request ⇒
        for {
          _ ← db.tryTransaction { implicit graph ⇒
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .existsOrFail()
          }
          _ ← authSrv
            .setPassword(userId, request.body('password))
        } yield Results.NoContent
      }

  def changePassword(userId: String): Action[AnyContent] =
    entryPoint("change password")
      .extract('password, FieldsParser[String])
      .extract('currentPassword, FieldsParser[String])
      .auth { implicit request ⇒
        if (userId == request.userId) {
          authSrv
            .changePassword(userId, request.body('currentPassword), request.body('password))
            .map(_ ⇒ Results.NoContent)
        } else Failure(AuthorizationError(s"You are not authorized to change password of $userId"))
      }

  def getKey(userId: String): Action[AnyContent] =
    entryPoint("get key")
      .auth { implicit request ⇒
        for {
          _ ← db.tryTransaction { implicit graph ⇒
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .existsOrFail()
          }
          key ← authSrv
            .getKey(userId)
        } yield Results.Ok(key)
      }

  def removeKey(userId: String): Action[AnyContent] =
    entryPoint("remove key")
      .auth { implicit request ⇒
        for {
          _ ← db.tryTransaction { implicit graph ⇒
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .getOrFail()
          }
          _ ← authSrv
            .removeKey(userId)
        } yield Results.NoContent
//          Failure(AuthorizationError(s"User $userId doesn't exist or permission is insufficient"))
      }

  def renewKey(userId: String): Action[AnyContent] =
    entryPoint("renew key")
      .auth { implicit request ⇒
        for {
          _ ← db.tryTransaction { implicit graph ⇒
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .existsOrFail()
          }
          key ← authSrv
            .renewKey(userId)
        } yield Results.Ok(key)
      }

  def search: Action[AnyContent] =
    entryPoint("search user")
      .extract('query, searchParser("listUser"))
      .authTransaction(db) { implicit request ⇒ graph ⇒
        val query: Query = request.body('query)
        val result       = queryExecutor.execute(query, graph, request.authContext)
        val resp         = Results.Ok((result.toJson \ "result").as[JsValue])
        result.toOutput match {
          case PagedResult(_, Some(size)) ⇒ Success(resp.withHeaders("X-Total" → size.toString))
          case _                          ⇒ Success(resp)
        }
      }
}
