package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.mvc._

import models.UserStatus
import services.UserSrv

import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.database.DBIndex
import org.elastic4play.services.AuthSrv
import org.elastic4play.{ AuthorizationError, Timed }

@Singleton
class AuthenticationCtrl @Inject() (
    authSrv: AuthSrv,
    userSrv: UserSrv,
    authenticated: Authenticated,
    dbIndex: DBIndex,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends AbstractController(components) {

  @Timed
  def login: Action[Fields] = Action.async(fieldsBodyParser) { implicit request ⇒
    dbIndex.getIndexStatus.flatMap {
      case false ⇒ Future.successful(Results.Status(520))
      case _ ⇒
        for {
          authContext ← authSrv.authenticate(request.body.getString("user").getOrElse("TODO"), request.body.getString("password").getOrElse("TODO"))
          user ← userSrv.get(authContext.userId)
        } yield {
          if (user.status() == UserStatus.Ok)
            authenticated.setSessingUser(Ok, authContext)
          else
            throw AuthorizationError("Your account is locked")
        }
    }
  }

  @Timed
  def logout = Action {
    Ok.withNewSession
  }
}