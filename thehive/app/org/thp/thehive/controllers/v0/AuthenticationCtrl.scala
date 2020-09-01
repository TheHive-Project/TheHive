package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.AuthorizationError
import org.thp.scalligraph.auth.{AuthSrv, RequestOrganisation}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services.UserSrv
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
@Singleton
class AuthenticationCtrl @Inject() (
    entrypoint: Entrypoint,
    authSrv: AuthSrv,
    requestOrganisation: RequestOrganisation,
    userSrv: UserSrv,
    @Named("with-thehive-schema") db: Database,
    implicit val ec: ExecutionContext
) {

  def logout: Action[AnyContent] = entrypoint("logout") { _ =>
    Success(Results.Ok.withNewSession)
  }

  def login: Action[AnyContent] =
    entrypoint("login")
      .extract("login", FieldsParser[String].on("user"))
      .extract("password", FieldsParser[String].on("password"))
      .extract("organisation", FieldsParser[String].optional.on("organisation"))
      .extract("code", FieldsParser[String].optional.on("code")) { implicit request =>
        val login: String                = request.body("login")
        val password: String             = request.body("password")
        val organisation: Option[String] = request.body("organisation") orElse requestOrganisation(request)
        val code: Option[String]         = request.body("code")
        db.roTransaction { implicit graph =>
          for {
            authContext <- authSrv.authenticate(login, password, organisation, code)
            user        <- db.roTransaction(userSrv.getOrFail(authContext.userId)(_))
            _           <- if (user.locked) Failure(AuthorizationError("Your account is locked")) else Success(())
            body = organisation.flatMap(userSrv.get(user).richUser(_).headOption).fold(user.toJson)(_.toJson)
          } yield authSrv.setSessionUser(authContext)(Results.Ok(body))
        }
      }
}
