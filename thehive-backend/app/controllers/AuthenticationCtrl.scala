package controllers

import javax.inject.{ Inject, Singleton }

import models.UserStatus
import org.elastic4play.Timed
import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.database.DBIndex
import org.elastic4play.services.AuthSrv
import play.api.mvc.{ Action, Controller, Results }
import services.UserSrv

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AuthenticationCtrl @Inject() (
    authSrv: AuthSrv,
    userSrv: UserSrv,
    authenticated: Authenticated,
    dbIndex: DBIndex,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends Controller {

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
            Unauthorized("Your account is locked")
        }
    }
  }

  @Timed
  def logout = Action {
    Ok.withNewSession
  }
}