package org.thp.thehive.controllers.v0

import scala.concurrent.ExecutionContext
import scala.util.Failure

import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.{AuthorizationError, RichOptionTry}
import org.thp.thehive.dto.v0.{InputUser, OutputUser}
import org.thp.thehive.models._
import org.thp.thehive.services._

@Singleton
class UserCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    userSrv: UserSrv,
    profileSrv: ProfileSrv,
    authSrv: AuthSrv,
    organisationSrv: OrganisationSrv,
    auditSrv: AuditSrv,
    implicit val ec: ExecutionContext
) extends QueryableCtrl {

  import UserConversion._

  lazy val logger                                           = Logger(getClass)
  override val entityName: String                           = "user"
  override val publicProperties: List[PublicProperty[_, _]] = userProperties(userSrv) ::: metaProperties[UserSteps]
  override val initialQuery: Query =
    Query.init[UserSteps]("listUser", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).users)
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, UserSteps, PagedResult[RichUser]](
    "page",
    FieldsParser[OutputParam],
    (range, userSteps, authContext) => userSteps.richUser(authContext.organisation).page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.output[RichUser, OutputUser]

  def current: Action[AnyContent] =
    entryPoint("current user")
      .authTransaction(db) { implicit request => implicit graph =>
        userSrv
          .get(request.userId)
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
              profile <- if (inputUser.roles.contains("admin")) profileSrv.getOrFail("admin")
              else if (inputUser.roles.contains("write")) profileSrv.getOrFail("analyst")
              else if (inputUser.roles.contains("read")) profileSrv.getOrFail("read-only")
              else ???
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

  def delete(userId: String): Action[AnyContent] =
    entryPoint("delete user")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          _ <- userSrv
            .current
            .can(Permissions.manageUser)
            .getOrFail()
          u <- userSrv
            .get(userId)
            .update("locked" -> true)
          _ <- auditSrv.deleteUser(u)
        } yield Results.NoContent
      }

  def get(userId: String): Action[AnyContent] =
    entryPoint("get user")
      .authTransaction(db) { request => implicit graph =>
        userSrv
          .get(userId)
          .richUser(request.organisation) // FIXME what if user is not in the same org ?
          .getOrFail()
          .map { user =>
            Results.Ok(user.toJson)
          }
      }

  def update(userId: String): Action[AnyContent] =
    entryPoint("update user")
      .extract("user", FieldsParser.update("user", userProperties(userSrv)))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("user")
        userSrv // Authorisation is managed in public properties
          .update(_.get(userId), propertyUpdaters)
          .flatMap { case (user, _) => user.richUser(request.organisation).getOrFail() }
          .map(user => Results.Ok(user.toJson))
      }

  def setPassword(userId: String): Action[AnyContent] =
    entryPoint("set password")
      .extract("password", FieldsParser[String].on("password"))
      .auth { implicit request =>
        for {
          _ <- db.tryTransaction { implicit graph =>
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .existsOrFail()
          }
          _ <- authSrv
            .setPassword(userId, request.body("password"))
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
      .auth { implicit request =>
        for {
          _ <- db.tryTransaction { implicit graph =>
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .existsOrFail()
          }
          key <- authSrv
            .getKey(userId)
        } yield Results.Ok(key)
      }

  def removeKey(userId: String): Action[AnyContent] =
    entryPoint("remove key")
      .auth { implicit request =>
        for {
          _ <- db.tryTransaction { implicit graph =>
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .getOrFail()
          }
          _ <- authSrv
            .removeKey(userId)
        } yield Results.NoContent
//          Failure(AuthorizationError(s"User $userId doesn't exist or permission is insufficient"))
      }

  def renewKey(userId: String): Action[AnyContent] =
    entryPoint("renew key")
      .auth { implicit request =>
        for {
          _ <- db.tryTransaction { implicit graph =>
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .existsOrFail()
          }
          key <- authSrv
            .renewKey(userId)
        } yield Results.Ok(key)
      }
}
