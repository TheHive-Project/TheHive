package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.{AuthorizationError, RichOptionTry}
import org.thp.thehive.dto.v1.{InputUser, OutputUser}
import org.thp.thehive.models._
import org.thp.thehive.services.{OrganisationSrv, ProfileSrv, UserSrv, UserSteps}
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
) extends QueryableCtrl {

  import UserConversion._

  override val entityName: String                           = "user"
  override val publicProperties: List[PublicProperty[_, _]] = userProperties(userSrv) ::: metaProperties[UserSteps]
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
  override val outputQuery: Query = Query.output[RichUser, OutputUser]
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
              user         <- userSrv.create(inputUser, organisation, profile)
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
      .authRoTransaction(db) { request => implicit graph =>
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
      .authRoTransaction(db) { implicit request => implicit graph =>
        for {
          _ <- userSrv
            .current
            .organisations(Permissions.manageUser)
            .users
            .get(userId)
            .existsOrFail()
          _ <- authSrv.setPassword(userId, request.body("password")) // FIXME
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
      .authRoTransaction(db) { implicit request => implicit graph =>
        for {
          _ <- userSrv
            .current
            .organisations(Permissions.manageUser)
            .users
            .get(userId)
            .existsOrFail()
          key <- authSrv // FIXME put outside tx
            .getKey(userId)
        } yield Results.Ok(key)
      }

  def removeKey(userId: String): Action[AnyContent] =
    entryPoint("remove key")
      .authRoTransaction(db) { implicit request => implicit graph =>
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
      .authRoTransaction(db) { implicit request => implicit graph =>
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
