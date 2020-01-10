package org.thp.thehive.controllers.v1
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import play.api.mvc.{Action, AnyContent, Results}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.{AuthenticationError, AuthorizationError, BadRequestError}
import org.thp.scalligraph.auth.{AuthSrv, RequestOrganisation}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{TOTPAuthSrv, UserSrv}
import org.thp.scalligraph.steps.StepsOps._

@Singleton
class AuthenticationCtrl @Inject()(
    entryPoint: EntryPoint,
    authSrv: AuthSrv,
    requestOrganisation: RequestOrganisation,
    userSrv: UserSrv,
    db: Database,
    implicit val ec: ExecutionContext
) {

  def login: Action[AnyContent] =
    entryPoint("login")
      .extract("login", FieldsParser[String].on("user"))
      .extract("password", FieldsParser[String].on("password"))
      .extract("organisation", FieldsParser[String].optional.on("organisation"))
      .extract("code", FieldsParser[String].optional.on("code")) { implicit request =>
        val login: String                = request.body("login")
        val password: String             = request.body("password")
        val organisation: Option[String] = request.body("organisation") orElse requestOrganisation(request)
        val code: Option[String]         = request.body("code")
        for {
          authContext <- authSrv.authenticate(login, password, organisation, code)
          user        <- db.roTransaction(userSrv.getOrFail(authContext.userId)(_))
          _           <- if (user.locked) Failure(AuthorizationError("Your account is locked")) else Success(())
        } yield authSrv.setSessionUser(authContext)(Results.Ok)
      }

  def withTotpAuthSrv[A](body: TOTPAuthSrv => Try[A]): Try[A] =
    authSrv match {
      case totpAuthSrv: TOTPAuthSrv if totpAuthSrv.enabled => body(totpAuthSrv)
      case _                                               => Failure(AuthenticationError("Operation not supported"))
    }

  def totpSetSecret: Action[AnyContent] =
    entryPoint("Set TOTP secret")
      .authTransaction(db) { implicit request => implicit graph =>
        withTotpAuthSrv { totpAuthSrv =>
          totpAuthSrv.getSecret(request.userId) match {
            case Some(_) => Failure(BadRequestError("TOTP is already configured"))
            case None    => totpAuthSrv.setSecret(request.userId).map(Results.Ok(_))
          }
        }
      }

  def totpUnsetSecret(userId: Option[String]): Action[AnyContent] =
    entryPoint("Unset TOTP secret")
      .authTransaction(db) { implicit request => implicit graph =>
        withTotpAuthSrv { totpAuthSrv =>
          userSrv
            .getOrFail(userId.getOrElse(request.userId))
            .flatMap { user =>
              if (request.userId == user.login || userSrv.current.organisations(Permissions.manageUser).users.get(user._id).exists())
                totpAuthSrv.unsetSecret(request.userId)
              else Failure(AuthorizationError("You cannot unset TOTP secret of this user"))
            }
            .map(_ => Results.NoContent)
        }
      }
}
