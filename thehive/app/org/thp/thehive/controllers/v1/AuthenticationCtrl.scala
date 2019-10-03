package org.thp.thehive.controllers.v1
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.AuthorizationError
import org.thp.scalligraph.auth.{AuthSrv, RequestOrganisation}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.UserSrv

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
      .extract("organisation", FieldsParser[String].optional.on("organisation")) { implicit request =>
        val login: String                = request.body("login")
        val password: String             = request.body("password")
        val organisation: Option[String] = request.body("organisation") orElse requestOrganisation(request)
        for {
          authContext <- authSrv.authenticate(login, password, organisation)
          user        <- db.roTransaction(userSrv.getOrFail(authContext.userId)(_))
          _           <- if (user.locked) Failure(AuthorizationError("Your account is locked")) else Success(())
        } yield authSrv.setSessionUser(authContext)(Results.Ok)
      }
}
