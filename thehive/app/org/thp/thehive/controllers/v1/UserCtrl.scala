package org.thp.thehive.controllers.v1

import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PublicProperties, Query}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.scalligraph.{AuthorizationError, BadRequestError, EntityIdOrName, NotFoundError, RichOptionTry}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputUser
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.http.HttpEntity
import play.api.libs.json.{JsNull, JsObject, Json}
import play.api.mvc._

import java.util.{Base64, Date}
import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}

case class UserOutputParam(from: Long, to: Long, extraData: Set[String], organisation: Option[String])

@Singleton
class UserCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    caseSrv: CaseSrv,
    userSrv: UserSrv,
    authSrv: AuthSrv,
    organisationSrv: OrganisationSrv,
    profileSrv: ProfileSrv,
    auditSrv: AuditSrv,
    attachmentSrv: AttachmentSrv,
    implicit val db: Database
) extends QueryableCtrl {

  override val entityName: String                 = "user"
  override val publicProperties: PublicProperties = properties.user

  override val initialQuery: Query =
    Query.init[Traversal.V[User]]("listUser", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).users)

  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[User]](
    "getUser",
    (idOrName, graph, authContext) => userSrv.get(idOrName)(graph).visible(authContext)
  )

  override val pageQuery: ParamQuery[UserOutputParam] = Query.withParam[UserOutputParam, Traversal.V[User], IteratorOutput](
    "page",
    (params, userSteps, authContext) =>
      params
        .organisation
        .fold(userSteps.richUser(authContext))(org => userSteps.richUser(authContext, EntityIdOrName(org)))
        .page(params.from, params.to, params.extraData.contains("total"))
  )
  override val outputQuery: Query =
    Query.outputWithContext[RichUser, Traversal.V[User]]((userSteps, authContext) => userSteps.richUser(authContext))

  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query.init[Traversal.V[User]]("currentUser", (graph, authContext) => userSrv.current(graph, authContext)),
    Query[Traversal.V[User], Traversal.V[Task]]("tasks", (userSteps, authContext) => userSteps.tasks.visible(organisationSrv)(authContext)),
    Query[Traversal.V[User], Traversal.V[Case]](
      "cases",
      (userSteps, authContext) =>
        caseSrv.startTraversal(userSteps.graph).visible(organisationSrv)(authContext).assignedTo(userSteps.value(_.login).toSeq: _*)
    )
  )
  def current: Action[AnyContent] =
    entrypoint("current user")
      .authRoTransaction(db) { implicit request => implicit graph =>
        userSrv
          .current
          .richUserWithCustomRenderer(request.organisation, _.organisationWithRole)
          .getOrFail("User")
          .map { user =>
            val scope =
              if (user._1.organisation == Organisation.administration.name) "admin"
              else "organisation"
            Results
              .Ok(user.toJson)
              .withHeaders("X-Organisation" -> request.organisation.toString)
              .withHeaders("X-Permissions" -> (Permissions.forScope(scope) & user._1.permissions).mkString(","))
          }
          .recover { case _ => Results.Unauthorized.withHeaders("X-Logout" -> "1") }
      }

  def create: Action[AnyContent] =
    entrypoint("create user")
      .extract("user", FieldsParser[InputUser])
      .auth { implicit request =>
        val inputUser: InputUser = request.body("user")
        db.tryTransaction { implicit graph =>
          val organisationName = inputUser.organisation.map(EntityIdOrName(_)).getOrElse(request.organisation)
          for {
            _            <- userSrv.current.organisations(Permissions.manageUser).get(organisationName).existsOrFail
            organisation <- organisationSrv.getOrFail(organisationName)
            profile      <- profileSrv.getOrFail(EntityIdOrName(inputUser.profile))
            user         <- userSrv.addOrCreateUser(inputUser.toUser, inputUser.avatar, organisation, profile)
          } yield user -> userSrv.canSetPassword(user.user)
        }.flatMap {
          case (user, true) =>
            inputUser
              .password
              .map(password => authSrv.setPassword(user.login, password))
              .flip
              .map(_ => Results.Created(user.toJson))
          case (user, _) => Success(Results.Created(user.toJson))
        }
      }

  def lock(userIdOrName: String): Action[AnyContent] =
    entrypoint("lock user")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          user <- userSrv.current.organisations(Permissions.manageUser).users.get(EntityIdOrName(userIdOrName)).getOrFail("User")
          _    <- userSrv.lock(user)
        } yield Results.NoContent
      }

  def delete(userIdOrName: String, organisation: Option[String]): Action[AnyContent] =
    entrypoint("delete user")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          org  <- organisationSrv.getOrFail(organisation.map(EntityIdOrName(_)).getOrElse(request.organisation))
          user <- userSrv.current.organisations(Permissions.manageUser).users.get(EntityIdOrName(userIdOrName)).getOrFail("User")
          _    <- userSrv.delete(user, org)
        } yield Results.NoContent
      }

  def get(userIdOrName: String): Action[AnyContent] =
    entrypoint("get user")
      .authRoTransaction(db) { implicit request => implicit graph =>
        userSrv
          .get(EntityIdOrName(userIdOrName))
          .visible
          .richUser
          .getOrFail("User")
          .map(user => Results.Ok(user.toJson))
      }

  def update(userIdOrName: String): Action[AnyContent] =
    entrypoint("update user")
      .extract("name", FieldsParser.string.optional.on("name"))
      .extract("organisation", FieldsParser.string.optional.on("organisation"))
      .extract("profile", FieldsParser.string.optional.on("profile"))
      .extract("locked", FieldsParser.boolean.optional.on("locked"))
      .extract("avatar", FieldsParser.string.optional.on("avatar"))
      .authTransaction(db) { implicit request => implicit graph =>
        val maybeName: Option[String]         = request.body("name")
        val maybeOrganisation: Option[String] = request.body("organisation")
        val maybeProfile: Option[String]      = request.body("profile")
        val maybeLocked: Option[Boolean]      = request.body("locked")
        val maybeAvatar: Option[String]       = request.body("avatar")
        val isCurrentUser: Boolean =
          userSrv
            .current
            .get(EntityIdOrName(userIdOrName))
            .exists

        val isUserAdmin: Boolean =
          userSrv
            .current
            .organisations(Permissions.manageUser)
            .users
            .get(EntityIdOrName(userIdOrName))
            .exists

        def requireAdmin[A](body: => Try[A]): Try[A] =
          if (isUserAdmin) body else Failure(AuthorizationError("You are not permitted to update this user"))

        userSrv.get(EntityIdOrName(userIdOrName)).visible.getOrFail("User").flatMap {
          case _ if !isCurrentUser && !isUserAdmin => Failure(AuthorizationError("You are not permitted to update this user"))
          case user =>
            auditSrv
              .mergeAudits {
                for {
                  updateName <-
                    maybeName
                      .map(name => userSrv.get(user).update(_.name, name).domainMap(_ => Json.obj("name" -> name)).getOrFail("User"))
                      .flip
                  updateLocked <-
                    maybeLocked
                      .map(locked => requireAdmin(if (locked) userSrv.lock(user) else userSrv.unlock(user)).map(_ => Json.obj("locked" -> locked)))
                      .flip
                  updateProfile <- maybeProfile.map { profileName =>
                    requireAdmin {
                      maybeOrganisation.fold[Try[JsObject]](Failure(BadRequestError("Organisation information is required to update user profile"))) {
                        organisationName =>
                          for {
                            profile      <- profileSrv.getOrFail(EntityIdOrName(profileName))
                            organisation <- organisationSrv.getOrFail(EntityIdOrName(organisationName))
                            _            <- userSrv.setProfile(user, organisation, profile)
                          } yield Json.obj("organisation" -> organisation.name, "profile" -> profile.name)
                      }
                    }
                  }.flip
                  updatedAvatar <- maybeAvatar.map {
                    case "" =>
                      userSrv.unsetAvatar(user)
                      Success(Json.obj("avatar" -> JsNull))
                    case avatar =>
                      attachmentSrv
                        .create(s"${user.login}.avatar", "image/jpeg", Base64.getDecoder.decode(avatar))
                        .flatMap(userSrv.setAvatar(user, _))
                        .map(_ => Json.obj("avatar" -> "[binary data]"))
                  }.flip
                } yield {
                  val updatedProperties = updateName.getOrElse(JsObject.empty) ++
                    updateLocked.getOrElse(JsObject.empty) ++
                    updateProfile.getOrElse(JsObject.empty) ++
                    updatedAvatar.getOrElse(JsObject.empty)
                  if (updatedProperties.fields.nonEmpty)
                    userSrv
                      .get(user)
                      .update(_._updatedBy, Some(request.userId))
                      .update(_._updatedAt, Some(new Date))
                      .iterate()
                  updatedProperties
                }
              }(update => auditSrv.user.update(user, update))
              .map(_ => Results.NoContent)
        }
      }

  def setPassword(userIdOrName: String): Action[AnyContent] =
    entrypoint("set password")
      .extract("password", FieldsParser[String].on("password"))
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(EntityIdOrName(userIdOrName))
              .getOrFail("User")
          }
          _ <- authSrv.setPassword(user.login, request.body("password"))
          _ <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("password" -> "<hidden>")))
        } yield Results.NoContent
      }

  def changePassword(userIdOrName: String): Action[AnyContent] =
    entrypoint("change password")
      .extract("password", FieldsParser[String].on("password"))
      .extract("currentPassword", FieldsParser[String].on("currentPassword"))
      .auth { implicit request =>
        for {
          user <- db.roTransaction(implicit graph => userSrv.current.get(EntityIdOrName(userIdOrName)).getOrFail("User"))
          _    <- authSrv.changePassword(user.login, request.body("currentPassword"), request.body("password"))
          _    <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("password" -> "<hidden>")))
        } yield Results.NoContent
      }

  def getKey(userIdOrName: String): Action[AnyContent] =
    entrypoint("get key")
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(EntityIdOrName(userIdOrName))
              .getOrFail("User")
          }
          key <- authSrv.getKey(user.login)
        } yield Results.Ok(key)
      }

  def removeKey(userIdOrName: String): Action[AnyContent] =
    entrypoint("remove key")
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(EntityIdOrName(userIdOrName))
              .getOrFail("User")
          }
          _ <- authSrv.removeKey(user.login)
          _ <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("key" -> "<hidden>")))
        } yield Results.NoContent
      //          Failure(AuthorizationError(s"User $userId doesn't exist or permission is insufficient"))
      }

  def renewKey(userIdOrName: String): Action[AnyContent] =
    entrypoint("renew key")
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .current
              .organisations(Permissions.manageUser)
              .users
              .get(EntityIdOrName(userIdOrName))
              .getOrFail("User")
          }
          key <- authSrv.renewKey(user.login)
          _   <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("key" -> "<hidden>")))
        } yield Results.Ok(key)
      }

  def avatar(userIdOrName: String): Action[AnyContent] =
    entrypoint("get user avatar")
      .authTransaction(db) { implicit request => implicit graph =>
        userSrv.get(EntityIdOrName(userIdOrName)).visible.avatar.headOption match {
          case Some(avatar) if attachmentSrv.exists(avatar) =>
            Success(
              Result(
                header = ResponseHeader(200),
                body = HttpEntity.Streamed(
                  attachmentSrv.source(avatar),
                  Some(avatar.size),
                  Some(avatar.contentType)
                )
              )
            )
          case _ => Failure(NotFoundError(s"user $userIdOrName has no avatar"))
        }
      }
}
