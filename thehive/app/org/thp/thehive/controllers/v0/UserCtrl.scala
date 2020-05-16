package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.{AuthorizationError, RichOptionTry}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputUser
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

@Singleton
class UserCtrl @Inject() (
    entrypoint: Entrypoint,
    db: Database,
    properties: Properties,
    userSrv: UserSrv,
    profileSrv: ProfileSrv,
    authSrv: AuthSrv,
    organisationSrv: OrganisationSrv,
    auditSrv: AuditSrv,
    implicit val ec: ExecutionContext
) extends QueryableCtrl {
  lazy val logger: Logger                                   = Logger(getClass)
  override val entityName: String                           = "user"
  override val publicProperties: List[PublicProperty[_, _]] = properties.user ::: metaProperties[UserSteps]

  override val initialQuery: Query =
    Query.init[UserSteps]("listUser", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).users)

  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, UserSteps](
    "getUser",
    FieldsParser[IdOrName],
    (param, graph, authContext) => userSrv.get(param.idOrName)(graph).visible(authContext)
  )

  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, UserSteps, PagedResult[RichUser]](
    "page",
    FieldsParser[OutputParam],
    (range, userSteps, authContext) => userSteps.richUser(authContext.organisation).page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query =
    Query.outputWithContext[RichUser, UserSteps]((userSteps, authContext) => userSteps.richUser(authContext.organisation))

  override val extraQueries: Seq[ParamQuery[_]] = Seq()

  def current: Action[AnyContent] =
    entrypoint("current user")
      .authRoTransaction(db) { implicit request => implicit graph =>
        userSrv
          .get(request.userId)
          .richUser(request.organisation)
          .getOrFail("User")
          .orElse(
            userSrv
              .get(request.userId)
              .richUser(OrganisationSrv.administration.name)
              .getOrFail("User")
          )
          .map(user => Results.Ok(user.toJson).withHeaders("X-Organisation" -> request.organisation))
      }

  def create: Action[AnyContent] =
    entrypoint("create user")
      .extract("user", FieldsParser[InputUser])
      .auth { implicit request =>
        val inputUser: InputUser = request.body("user")
        db.tryTransaction { implicit graph =>
            val organisationName = inputUser.organisation.getOrElse(request.organisation)
            for {
              _            <- userSrv.current.organisations(Permissions.manageUser).get(organisationName).existsOrFail()
              organisation <- organisationSrv.getOrFail(organisationName)
              profile <- if (inputUser.roles.contains("admin")) profileSrv.getOrFail(ProfileSrv.admin.name)
              else if (inputUser.roles.contains("write")) profileSrv.getOrFail(ProfileSrv.analyst.name)
              else if (inputUser.roles.contains("read")) profileSrv.getOrFail(ProfileSrv.readonly.name)
              else profileSrv.getOrFail(ProfileSrv.readonly.name)
              user <- userSrv.addOrCreateUser(inputUser.toUser, inputUser.avatar, organisation, profile)
            } yield user -> userSrv.canSetPassword(user.user)
          }
          .flatMap {
            case (user, true) =>
              inputUser
                .password
                .map(password => authSrv.setPassword(user._id, password))
                .flip
                .map(_ => Results.Created(user.toJson))
            case (user, _) => Success(Results.Created(user.toJson))
          }
      }

  def lock(userId: String): Action[AnyContent] =
    entrypoint("lock user")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          user <- userSrv.current.organisations(Permissions.manageUser).users.get(userId).getOrFail("User")
          _    <- userSrv.lock(user)
        } yield Results.NoContent
      }

  def delete(userId: String): Action[AnyContent] =
    entrypoint("delete user")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          organisation <- userSrv.current.organisations(Permissions.manageUser).has("name", request.organisation).getOrFail("Organisation")
          user         <- organisationSrv.get(organisation).users.get(userId).getOrFail("User")
          _            <- userSrv.delete(user, organisation)
        } yield Results.NoContent
      }

  def get(userId: String): Action[AnyContent] =
    entrypoint("get user")
      .authRoTransaction(db) { implicit request => implicit graph =>
        userSrv
          .get(userId)
          .visible
          .richUser(request.organisation)
          .getOrFail("User")
          .map(user => Results.Ok(user.toJson))
      }

  def update(userId: String): Action[AnyContent] =
    entrypoint("update user")
      .extract("user", FieldsParser.update("user", properties.user))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("user")
        for {
          user <- userSrv
            .update(userSrv.get(userId), propertyUpdaters) // Authorisation is managed in public properties
            .flatMap { case (user, _) => user.richUser(request.organisation).getOrFail("User") }
        } yield Results.Ok(user.toJson)

      }

  def setPassword(userId: String): Action[AnyContent] =
    entrypoint("set password")
      .extract("password", FieldsParser[String].on("password"))
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .get(userId)
              .getOrFail("User")
              .flatMap { u =>
                userSrv
                  .current
                  .organisations(Permissions.manageUser)
                  .users
                  .get(u)
                  .getOrFail("User")
              }
          }
          _ <- authSrv.setPassword(userId, request.body("password"))
          _ <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("password" -> "<hidden>")))
        } yield Results.NoContent
      }

  def changePassword(userId: String): Action[AnyContent] =
    entrypoint("change password")
      .extract("password", FieldsParser[String].on("password"))
      .extract("currentPassword", FieldsParser[String].on("currentPassword"))
      .auth { implicit request =>
        if (userId == request.userId) {
          for {
            user <- db.roTransaction(implicit graph => userSrv.get(userId).getOrFail("User"))
            _    <- authSrv.changePassword(userId, request.body("currentPassword"), request.body("password"))
            _    <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("password" -> "<hidden>")))
          } yield Results.NoContent
        } else Failure(AuthorizationError(s"You are not authorized to change password of $userId"))
      }

  def getKey(userId: String): Action[AnyContent] =
    entrypoint("get key")
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .get(userId)
              .getOrFail("User")
              .flatMap { u =>
                userSrv
                  .current
                  .organisations(Permissions.manageUser)
                  .users
                  .get(u)
                  .getOrFail("User")
              }
          }
          key <- authSrv
            .getKey(user._id)
        } yield Results.Ok(key)
      }

  def removeKey(userId: String): Action[AnyContent] =
    entrypoint("remove key")
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .get(userId)
              .getOrFail("User")
              .flatMap { u =>
                userSrv
                  .current
                  .organisations(Permissions.manageUser)
                  .users
                  .get(u)
                  .getOrFail("User")
              }
          }
          _ <- authSrv.removeKey(userId)
          _ <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("key" -> "<hidden>")))
        } yield Results.NoContent
//          Failure(AuthorizationError(s"User $userId doesn't exist or permission is insufficient"))
      }

  def renewKey(userId: String): Action[AnyContent] =
    entrypoint("renew key")
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .get(userId)
              .getOrFail("User")
              .flatMap { u =>
                userSrv
                  .current
                  .organisations(Permissions.manageUser)
                  .users
                  .get(u)
                  .getOrFail("User")
              }
          }
          key <- authSrv.renewKey(userId)
          _   <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("key" -> "<hidden>")))
        } yield Results.Ok(key)
      }
}
