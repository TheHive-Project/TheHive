package org.thp.thehive.controllers.v0

import scala.concurrent.ExecutionContext

import play.api.Configuration
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.UserSrv

@Singleton
class AuthenticationCtrl @Inject()(
    entryPoint: EntryPoint,
    authenticated: AuthenticateSrv,
    configuration: Configuration,
    authSrv: AuthSrv,
    userSrv: UserSrv,
    db: Database,
    implicit val ec: ExecutionContext) {

  def login: Action[AnyContent] =
    entryPoint("login")
      .extract('login, FieldsParser[String].on("user"))
      .extract('password, FieldsParser[String].on("password")) { implicit request ⇒
        val login: String    = request.body('login)
        val password: String = request.body('password)
        authSrv
          .authenticate(login, password)
          .map { authContext ⇒
//          val user = db.transaction(userSrv.getOrFail(authContext.userId)(_))
//          if (user.status == UserStatus.ok)
            authenticated.setSessingUser(Results.Ok, authContext)
//          else Results.Unauthorized("Your account is locked")
          }
      }
}
