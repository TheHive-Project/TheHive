package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.UserSrv
import play.api.Configuration
import play.api.mvc.{Action, AnyContent, Results}
import scala.concurrent.ExecutionContext
import scala.util.Success

@Singleton
class AuthenticationCtrl @Inject()(
    entryPoint: EntryPoint,
    authenticated: AuthenticateSrv,
    configuration: Configuration,
    authSrv: AuthSrv,
    userSrv: UserSrv,
    db: Database,
    implicit val ec: ExecutionContext
) {

  val organisationHeader: String = configuration.get[String]("auth.organisationHeader")

  def logout: Action[AnyContent] = entryPoint("logout") { _ =>
    Success(Results.Ok.withNewSession)
  }

  def login: Action[AnyContent] =
    entryPoint("login")
      .extract('login, FieldsParser[String].on("user"))
      .extract('password, FieldsParser[String].on("password"))
      .extract('organisation, FieldsParser[String].optional.on("organisation")) { implicit request =>
        val login: String                = request.body('login)
        val password: String             = request.body('password)
        val organisation: Option[String] = request.body('organisation) orElse request.headers.get(organisationHeader)
        authSrv
          .authenticate(login, password, organisation)
          .map { authContext =>
//          val user = db.transaction(userSrv.getOrFail(authContext.userId)(_))
//          if (user.status == UserStatus.ok)
            authenticated.setSessingUser(Results.Ok, authContext)
//          else Results.Unauthorized("Your account is locked")
          }
      }
}
