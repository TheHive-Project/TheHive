package services

import javax.inject.{ Inject, Singleton }

import scala.annotation.implicitNotFound
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

import play.api.libs.json.{ JsObject, JsString }
import play.api.mvc.RequestHeader

import org.elastic4play.{ AuthenticationError, AuthorizationError }
import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ AuthCapability, AuthContext, AuthSrv, UpdateSrv }
import org.elastic4play.utils.Hasher

import models.{ User, UserModel }

@Singleton
class LocalAuthSrv @Inject() (userModel: UserModel,
                              userSrv: UserSrv,
                              updateSrv: UpdateSrv,
                              implicit val ec: ExecutionContext) extends AuthSrv {

  val name = "local"
  def capabilities = Set(AuthCapability.changePassword, AuthCapability.setPassword)

  private[services] def doAuthenticate(user: User, password: String): Boolean = {
    user.password().map(_.split(",", 2)).fold(false) {
      case Array(seed, pwd) =>
        val hash = Hasher("SHA-256").fromString(seed + password).head.toString
        hash == pwd
      case _ => false
    }
  }

  def authenticate(username: String, password: String)(implicit request: RequestHeader): Future[AuthContext] = {
    userSrv.get(username).flatMap { user =>
      if (doAuthenticate(user, password)) userSrv.getFromUser(request, user)
      else Future.failed(AuthenticationError("Authentication failure"))
    }
  }

  def changePassword(username: String, oldPassword: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] = {
    userSrv.get(username).flatMap { user =>
      if (doAuthenticate(user, oldPassword)) setPassword(username, newPassword)
      else Future.failed(AuthorizationError("Authentication failure"))
    }
  }

  def setPassword(username: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] = {
    val seed = Random.nextString(10).replace(',', '!')
    val newHash = seed + "," + Hasher("SHA-256").fromString(seed + newPassword).head.toString
    userSrv.update(username, Fields(JsObject(Seq("password" -> JsString(newHash)))))
      .map(_ => ())
  }
}