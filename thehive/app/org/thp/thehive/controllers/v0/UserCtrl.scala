package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{Entrypoint, FString, FieldsParser}
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.scalligraph.{AuthorizationError, EntityIdOrName, EntityName, InvalidFormatAttributeError, RichOptionTry}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputUser
import org.thp.thehive.models._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Failure, Success, Try}

@Singleton
class UserCtrl @Inject() (
    override val entrypoint: Entrypoint,
    userSrv: UserSrv,
    profileSrv: ProfileSrv,
    authSrv: AuthSrv,
    organisationSrv: OrganisationSrv,
    auditSrv: AuditSrv,
    @Named("with-thehive-schema") implicit override val db: Database,
    @Named("v0") override val queryExecutor: QueryExecutor,
    override val publicData: PublicUser
) extends QueryCtrl {
  def current: Action[AnyContent] =
    entrypoint("current user")
      .authRoTransaction(db) { implicit request => implicit graph =>
        userSrv
          .current
          .richUser
          .getOrFail("User")
          .orElse(
            userSrv
              .current
              .richUser(request, EntityName(Organisation.administration.name))
              .getOrFail("User")
          )
          .map(user => Results.Ok(user.toJson).withHeaders("X-Organisation" -> request.organisation.toString))
      }

  def create: Action[AnyContent] =
    entrypoint("create user")
      .extract("user", FieldsParser[InputUser])
      .auth { implicit request =>
        val inputUser: InputUser = request.body("user")
        db.tryTransaction { implicit graph =>
          val organisationIdOrName = inputUser.organisation.map(EntityIdOrName(_)).getOrElse(request.organisation)
          for {
            _            <- userSrv.current.organisations(Permissions.manageUser).get(organisationIdOrName).existsOrFail
            organisation <- organisationSrv.getOrFail(organisationIdOrName)
            profile <-
              if (inputUser.roles.contains("admin")) profileSrv.getOrFail(EntityName(Profile.admin.name))
              else if (inputUser.roles.contains("write")) profileSrv.getOrFail(EntityName(Profile.analyst.name))
              else if (inputUser.roles.contains("read")) profileSrv.getOrFail(EntityName(Profile.readonly.name))
              else profileSrv.getOrFail(EntityName(Profile.readonly.name))
            user <- userSrv.addOrCreateUser(inputUser.toUser, inputUser.avatar, organisation, profile)
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

  def lock(userId: String): Action[AnyContent] =
    entrypoint("lock user")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          user <- userSrv.current.organisations(Permissions.manageUser).users.get(EntityIdOrName(userId)).getOrFail("User")
          _    <- userSrv.lock(user)
        } yield Results.NoContent
      }

  def delete(userId: String): Action[AnyContent] =
    entrypoint("delete user")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          organisation <- userSrv.current.organisations(Permissions.manageUser).get(request.organisation).getOrFail("Organisation")
          user         <- organisationSrv.get(organisation).users.get(EntityIdOrName(userId)).getOrFail("User")
          _            <- userSrv.delete(user, organisation)
        } yield Results.NoContent
      }

  def get(userId: String): Action[AnyContent] =
    entrypoint("get user")
      .authRoTransaction(db) { implicit request => implicit graph =>
        userSrv
          .get(EntityIdOrName(userId))
          .visible
          .richUser
          .getOrFail("User")
          .map(user => Results.Ok(user.toJson))
      }

  def update(userId: String): Action[AnyContent] =
    entrypoint("update user")
      .extract("user", FieldsParser.update("user", publicData.publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("user")
        for {
          user <-
            userSrv
              .update(userSrv.get(EntityIdOrName(userId)), propertyUpdaters) // Authorisation is managed in public properties
              .flatMap { case (user, _) => user.richUser.getOrFail("User") }
        } yield Results.Ok(user.toJson)
      }

  def setPassword(userId: String): Action[AnyContent] =
    entrypoint("set password")
      .extract("password", FieldsParser[String].on("password"))
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .get(EntityIdOrName(userId))
              .getOrFail("User")
              .flatMap { u =>
                userSrv
                  .current
                  .organisations(Permissions.manageUser)
                  .users
                  .getEntity(u)
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
        if (userId == request.userId)
          for {
            user <- db.roTransaction(implicit graph => userSrv.get(EntityIdOrName(userId)).getOrFail("User"))
            _    <- authSrv.changePassword(userId, request.body("currentPassword"), request.body("password"))
            _    <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("password" -> "<hidden>")))
          } yield Results.NoContent
        else Failure(AuthorizationError(s"You are not authorized to change password of $userId"))
      }

  def getKey(userId: String): Action[AnyContent] =
    entrypoint("get key")
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .get(EntityIdOrName(userId))
              .getOrFail("User")
              .flatMap { u =>
                userSrv
                  .current
                  .organisations(Permissions.manageUser)
                  .users
                  .getEntity(u)
                  .getOrFail("User")
              }
          }
          key <-
            authSrv
              .getKey(user.login)
        } yield Results.Ok(key)
      }

  def removeKey(userId: String): Action[AnyContent] =
    entrypoint("remove key")
      .auth { implicit request =>
        for {
          user <- db.roTransaction { implicit graph =>
            userSrv
              .get(EntityIdOrName(userId))
              .getOrFail("User")
              .flatMap { u =>
                userSrv
                  .current
                  .organisations(Permissions.manageUser)
                  .users
                  .getEntity(u)
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
              .get(EntityIdOrName(userId))
              .getOrFail("User")
              .flatMap { u =>
                userSrv
                  .current
                  .organisations(Permissions.manageUser)
                  .users
                  .getEntity(u)
                  .getOrFail("User")
              }
          }
          key <- authSrv.renewKey(userId)
          _   <- db.tryTransaction(implicit graph => auditSrv.user.update(user, Json.obj("key" -> "<hidden>")))
        } yield Results.Ok(key)
      }
}

@Singleton
class PublicUser @Inject() (userSrv: UserSrv, organisationSrv: OrganisationSrv, @Named("with-thehive-schema") db: Database) extends PublicData {
  override val entityName: String = "user"
  override val initialQuery: Query =
    Query.init[Traversal.V[User]]("listUser", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).users)
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[User]](
    "getUser",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, authContext) => userSrv.get(idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[User], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, userSteps, authContext) => userSteps.richUser(authContext).page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query =
    Query.outputWithContext[RichUser, Traversal.V[User]]((userSteps, authContext) => userSteps.richUser(authContext))
  override val extraQueries: Seq[ParamQuery[_]] = Seq()
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[User]
    .property("login", UMapping.string)(_.field.readonly)
    .property("name", UMapping.string)(_.field.custom { (_, value, vertex, graph, authContext) =>
      def isCurrentUser: Try[Unit] =
        userSrv.get(vertex)(graph).current(authContext).existsOrFail

      def isUserAdmin: Try[Unit] =
        userSrv
          .current(graph, authContext)
          .organisations(Permissions.manageUser)
          .users
          .getElement(vertex)
          .existsOrFail

      isCurrentUser
        .orElse(isUserAdmin)
        .map { _ =>
          UMapping.string.setProperty(vertex, "name", value)
          Json.obj("name" -> value)
        }
    })
    .property("status", UMapping.string)(
      _.select(_.choose(predicate = _.value(_.locked).is(P.eq(true)), onTrue = "Locked", onFalse = "Ok"))
        .custom { (_, value, vertex, graph, authContext) =>
          userSrv
            .current(graph, authContext)
            .organisations(Permissions.manageUser)
            .users
            .getElement(vertex)
            .orFail(AuthorizationError("Operation not permitted"))
            .flatMap {
              case user if value == "Ok" =>
                userSrv.unlock(user)(graph, authContext)
                Success(Json.obj("status" -> value))
              case user if value == "Locked" =>
                userSrv.lock(user)(graph, authContext)
                Success(Json.obj("status" -> value))
              case _ => Failure(InvalidFormatAttributeError("status", "UserStatus", Set("Ok", "Locked"), FString(value)))
            }
        }
    )
    .build

}
