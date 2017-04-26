package controllers

import javax.inject.{ Inject, Singleton }

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import play.api.mvc.{ Action, Controller }
import org.elastic4play.Timed
import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.services.AuthSrv
import services.UserSrv
import models.UserStatus

@Singleton
class AuthenticationCtrl @Inject() (
    authSrv: AuthSrv,
    userSrv: UserSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends Controller {

  @Timed
  def login: Action[Fields] = Action.async(fieldsBodyParser) { implicit request ⇒
    authSrv.authenticate(request.body.getString("user").getOrElse("TODO"), request.body.getString("password").getOrElse("TODO"))
      .flatMap { authContext ⇒ userSrv.get(authContext.userId).map(authContext → _) }
      .map {
        case (authContext, user) if user.status() == UserStatus.Ok ⇒ authenticated.setSessingUser(Ok, authContext)
        case _                                                     ⇒ Unauthorized("Your account is locked")
      }
  }

  @Timed
  def logout = Action {
    Ok.withNewSession
  }
}