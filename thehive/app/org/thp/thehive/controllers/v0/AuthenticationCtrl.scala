package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.AuthorizationError
import org.thp.scalligraph.auth.{AuthSrv, RequestOrganisation}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.services.UserSrv
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

@Singleton
class AuthenticationCtrl @Inject()(
    entryPoint: EntryPoint,
    authSrv: AuthSrv,
    requestOrganisation: RequestOrganisation,
    userSrv: UserSrv,
    db: Database,
    implicit val ec: ExecutionContext
) {

  def logout: Action[AnyContent] = entryPoint("logout") { _ =>
    Success(Results.Ok.withNewSession)
  }

  def login: Action[AnyContent] =
    entryPoint("login")
      .extract("login", FieldsParser[String].on("user"))
      .extract("password", FieldsParser[String].on("password"))
      .extract("organisation", FieldsParser[String].optional.on("organisation")) { implicit request =>
        val login: String                = request.body("login")
        val password: String             = request.body("password")
        val organisation: Option[String] = request.body("organisation") orElse requestOrganisation(request)
        db.roTransaction { implicit graph =>
          for {
            authContext <- authSrv.authenticate(login, password, organisation)
            user        <- db.roTransaction(userSrv.getOrFail(authContext.userId)(_))
            _           <- if (user.locked) Failure(AuthorizationError("Your account is locked")) else Success(())
          } yield {
            val r = {
              for {
                orga     <- Try(organisation.get)
                richUser <- userSrv.get(user).richUser(orga).getOrFail()
              } yield Results.Ok(richUser.toJson)
            } getOrElse Results.Ok(user.toJson)

            authSrv.setSessionUser(authContext)(r)
          }
        }
      }
}
