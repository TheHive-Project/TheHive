package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.{AuthorizationError, RichOptionTry}
import org.thp.thehive.dto.v0.{InputUser, OutputUser}
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext
import scala.util.Failure

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
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, UserSteps](
    "get",
    FieldsParser[IdOrName],
    (param, graph, authContext) => userSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, UserSteps, PagedResult[RichUser]](
    "page",
    FieldsParser[OutputParam],
    (range, userSteps, authContext) => userSteps.richUser(authContext.organisation).page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.output[RichUser, OutputUser]
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[UserSteps, List[RichUser]]("toList", (userSteps, authContext) => userSteps.richUser(authContext.organisation).toList)
  )

  def current: Action[AnyContent] =
    entryPoint("current user")
      .authRoTransaction(db) { implicit request => implicit graph =>
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
              else profileSrv.getOrFail("read-only")
              user <- userSrv.create(inputUser, organisation, profile)
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
          _ <- auditSrv.user.delete(u)
        } yield Results.NoContent
      }

  def get(userId: String): Action[AnyContent] =
    entryPoint("get user")
      .authRoTransaction(db) { request => implicit graph =>
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
        for {
          user <- userSrv
            .update(_.get(userId), propertyUpdaters) // Authorisation is managed in public properties
            .flatMap { case (user, _) => user.richUser(request.organisation).getOrFail() }
        } yield Results.Ok(user.toJson)

      }

  def setPassword(userId: String): Action[AnyContent] =
    entryPoint("set password")
      .extract("password", FieldsParser[String].on("password"))
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          user <- userSrv
            .current
            .organisations(Permissions.manageUser)
            .users
            .get(userId)
            .getOrFail()
          _ <- authSrv.setPassword(userId, request.body("password"))
          _ <- auditSrv.user.update(user, Json.obj("password" -> "<hidden>"))
        } yield Results.NoContent
      }

  def changePassword(userId: String): Action[AnyContent] =
    entryPoint("change password")
      .extract("password", FieldsParser[String].on("password"))
      .extract("currentPassword", FieldsParser[String].on("currentPassword"))
      .auth { implicit request =>
        if (userId == request.userId) {
          for {
            user <- db.roTransaction(implicit graph => userSrv.get(userId).getOrFail())
            _    <- authSrv.changePassword(userId, request.body("currentPassword"), request.body("password"))
            _    <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("password" -> "<hidden>")))
          } yield Results.NoContent
        } else Failure(AuthorizationError(s"You are not authorized to change password of $userId"))
      }

  def getKey(userId: String): Action[AnyContent] =
    entryPoint("get key")
      .auth { implicit request =>
        for {
          _ <- db.roTransaction { implicit graph =>
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
          user <- db.roTransaction { implicit graph =>
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .getOrFail()
          }
          _ <- authSrv.removeKey(userId)
          _ <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("key" -> "<hidden>")))
        } yield Results.NoContent
//          Failure(AuthorizationError(s"User $userId doesn't exist or permission is insufficient"))
      }

  def renewKey(userId: String): Action[AnyContent] =
    entryPoint("renew key")
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(userId)
              .getOrFail()
          }
          key <- authSrv.renewKey(userId)
          _   <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("key" -> "<hidden>")))
        } yield Results.Ok(key)
      }
}
