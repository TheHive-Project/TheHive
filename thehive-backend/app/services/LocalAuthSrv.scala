package services

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

import play.api.mvc.RequestHeader

import akka.stream.Materializer
import models.User

import org.elastic4play.controllers.Fields
import org.elastic4play.services.{AuthCapability, AuthContext, AuthSrv}
import org.elastic4play.utils.Hasher
import org.elastic4play.{AuthenticationError, AuthorizationError}

@Singleton
class LocalAuthSrv @Inject()(userSrv: UserSrv, implicit val ec: ExecutionContext, implicit val mat: Materializer) extends AuthSrv {

  val name                  = "local"
  override val capabilities = Set(AuthCapability.changePassword, AuthCapability.setPassword)

  private[services] def doAuthenticate(user: User, password: String): Boolean =
    user.password().map(_.split(",", 2)).fold(false) {
      case Array(seed, pwd) ⇒
        val hash = Hasher("SHA-256").fromString(seed + password).head.toString
        hash == pwd
      case _ ⇒ false
    }

  override def authenticate(username: String, password: String)(implicit request: RequestHeader): Future[AuthContext] =
    userSrv.get(username).flatMap { user ⇒
      if (doAuthenticate(user, password)) userSrv.getFromUser(request, user, name)
      else Future.failed(AuthenticationError("Authentication failure"))
    }

  override def changePassword(username: String, oldPassword: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] =
    userSrv.get(username).flatMap { user ⇒
      if (doAuthenticate(user, oldPassword)) setPassword(username, newPassword)
      else Future.failed(AuthorizationError("Authentication failure"))
    }

  override def setPassword(username: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] = {
    val seed    = Random.nextString(10).replace(',', '!')
    val newHash = seed + "," + Hasher("SHA-256").fromString(seed + newPassword).head.toString
    userSrv.update(username, Fields.empty.set("password", newHash)).map(_ ⇒ ())
  }
}
