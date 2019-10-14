package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.{AuthorizationError, BadRequestError, RichOptionTry}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.{InputUser, OutputUser}
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

@Singleton
class UserCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    properties: Properties,
    userSrv: UserSrv,
    authSrv: AuthSrv,
    organisationSrv: OrganisationSrv,
    profileSrv: ProfileSrv,
    auditSrv: AuditSrv,
    implicit val ec: ExecutionContext
) extends QueryableCtrl {

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
  override val outputQuery: Query = Query.output[RichUser]()
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[UserSteps, List[RichUser]]("toList", (userSteps, authContext) => userSteps.richUser(authContext.organisation).toList)
  )

  def current: Action[AnyContent] =
    entryPoint("current user")
      .authRoTransaction(db) { implicit request => implicit graph =>
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
              user         <- userSrv.create(inputUser.toUser, organisation, profile)
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

  // FIXME delete = lock or remove from organisation ?
  // lock make user unusable for all organisation
  // remove from organisation make the user disappear from organisation admin, and his profile is removed
  def delete(userId: String): Action[AnyContent] =
    entryPoint("delete user")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          user        <- userSrv.get(userId).getOrFail()
          _           <- userSrv.current.organisations(Permissions.manageUser).users.get(user).getOrFail()
          updatedUser <- userSrv.get(user).update("locked" -> true)
          _           <- auditSrv.user.delete(updatedUser)
        } yield Results.NoContent
      }

  def get(userId: String): Action[AnyContent] =
    entryPoint("get user")
      .authRoTransaction(db) { implicit request => implicit graph =>
        userSrv
          .get(userId)
          .visible
          .richUser(request.organisation)
          .getOrFail()
          .map(user => Results.Ok(user.toJson))
      }

  def update(userId: String): Action[AnyContent] =
    entryPoint("update user")
      .extract("name", FieldsParser.string.optional.on("name"))
      .extract("organisation", FieldsParser.string.optional.on("organisation"))
      .extract("profile", FieldsParser.string.optional.on("profile"))
      .extract("locked", FieldsParser.boolean.optional.on("locked"))
      .authTransaction(db) { implicit request => implicit graph =>
        val maybeName: Option[String]         = request.body("name")
        val maybeOrganisation: Option[String] = request.body("organisation")
        val maybeProfile: Option[String]      = request.body("profile")
        val maybeLocked: Option[Boolean]      = request.body("locked")
        userSrv.get(userId).getOrFail().flatMap { user =>
          auditSrv
            .mergeAudits {
              for {
                updateName   <- maybeName.map(name => userSrv.get(user).update("name"       -> name).map(_ => Json.obj("name"     -> name))).flip
                updateLocked <- maybeLocked.map(locked => userSrv.get(user).update("locked" -> locked).map(_ => Json.obj("locked" -> locked))).flip
                updateProfile <- maybeProfile.fold[Try[JsObject]](Success(JsObject.empty)) { profileName =>
                  maybeOrganisation.fold[Try[JsObject]](Failure(BadRequestError("Organisation information is required to update user profile"))) {
                    organisationName =>
                      for {
                        profile      <- profileSrv.getOrFail(profileName)
                        organisation <- organisationSrv.getOrFail(organisationName)
                        _            <- userSrv.setProfile(user, organisation, profile)
                      } yield Json.obj("organisation" -> organisation.name, "profile" -> profile.name)
                  }
                }
              } yield updateName.getOrElse(JsObject.empty) ++ updateLocked.getOrElse(JsObject.empty) ++ updateProfile
            }(update => auditSrv.user.update(user, update))
            .map(_ => Results.NoContent)
        }
      }

  def setPassword(userId: String): Action[AnyContent] =
    entryPoint("set password")
      .extract("password", FieldsParser[String].on("password"))
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .get(userId)
              .getOrFail()
              .flatMap { u =>
                userSrv
                  .current
                  .organisations(Permissions.manageUser)
                  .users
                  .get(u)
                  .getOrFail()
              }
          }
          _ <- authSrv.setPassword(userId, request.body("password"))
          _ <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("password" -> "<hidden>")))
        } yield Results.NoContent
      }

  def changePassword(userId: String): Action[AnyContent] =
    entryPoint("change password")
      .extract("password", FieldsParser[String])
      .extract("currentPassword", FieldsParser[String])
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
          user <- db.roTransaction { implicit graph =>
            userSrv
              .get(userId)
              .getOrFail()
              .flatMap { u =>
                userSrv
                  .current
                  .organisations(Permissions.manageUser)
                  .users
                  .get(u)
                  .getOrFail()
              }
          }
          key <- authSrv.getKey(user._id)
        } yield Results.Ok(key)
      }

  def removeKey(userId: String): Action[AnyContent] =
    entryPoint("remove key")
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .get(userId)
              .getOrFail()
              .flatMap { u =>
                userSrv
                  .current
                  .organisations(Permissions.manageUser)
                  .users
                  .get(u)
                  .getOrFail()
              }
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
              .get(userId)
              .getOrFail()
              .flatMap { u =>
                userSrv
                  .current
                  .organisations(Permissions.manageUser)
                  .users
                  .get(u)
                  .getOrFail()
              }
          }
          key <- authSrv.renewKey(userId)
          _   <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("key" -> "<hidden>")))
        } yield Results.Ok(key)
      }
}
