package controllers

import javax.inject.{ Inject, Singleton }

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext

import play.api.mvc.{ Action, Controller }

import org.elastic4play.Timed
import org.elastic4play.controllers.{ Authenticated, FieldsBodyParser, Renderer }
import org.elastic4play.services.AuthSrv

@Singleton
class AuthenticationCtrl @Inject() (
    authSrv: AuthSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends Controller {

  @Timed
  def login = Action.async(fieldsBodyParser) { implicit request ⇒
    authSrv.authenticate(request.body.getString("user").getOrElse("TODO"), request.body.getString("password").getOrElse("TODO"))
      .map { authContext ⇒ authenticated.setSessingUser(Ok, authContext) }
  }

  @Timed
  def logout = Action {
    Ok.withNewSession
  }
}