package org.thp.thehive.controllers.v0

import org.thp.scalligraph.auth.{AuthSrv, RequestOrganisation}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.{AuthorizationError, EntityIdOrName, EntityName}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.services.{TheHiveOpsNoDeps, UserSrv}
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class AuthenticationCtrl(
    entrypoint: Entrypoint,
    authSrv: AuthSrv,
    requestOrganisation: RequestOrganisation,
    userSrv: UserSrv,
    db: Database,
    implicit val ec: ExecutionContext
) extends TheHiveOpsNoDeps {

  def logout: Action[AnyContent] =
    entrypoint("logout") { _ =>
      Success(Results.Ok.withNewSession)
    }

  def login: Action[AnyContent] =
    entrypoint("login")
      .extract("login", FieldsParser[String].on("user"))
      .extract("password", FieldsParser[String].on("password"))
      .extract("organisation", FieldsParser[String].optional.on("organisation"))
      .extract("code", FieldsParser[String].optional.on("code")) { implicit request =>
        val login: String                        = request.body("login")
        val password: String                     = request.body("password")
        val organisation: Option[EntityIdOrName] = request.body("organisation").map(EntityIdOrName(_)) orElse requestOrganisation(request)
        val code: Option[String]                 = request.body("code")
        for {
          authContext <- authSrv.authenticate(login, password, organisation, code)
          user        <- db.roTransaction(userSrv.get(EntityName(authContext.userId))(_).richUser(authContext).getOrFail("User"))
          _           <- if (user.locked) Failure(AuthorizationError("Your account is locked")) else Success(())
        } yield authSrv.setSessionUser(authContext)(Results.Ok(user.toJson))
      }
}
