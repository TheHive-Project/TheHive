package org.thp.thehive.services

import scala.util.{Failure, Random, Try}

import play.api.Logger
import play.api.mvc.RequestHeader

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.{AuthCapability, AuthContext, AuthSrv}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.{AuthenticationError, AuthorizationError, Hasher}
import org.thp.thehive.models.User

object LocalAuthSrv {

  def hashPassword(password: String): String = {
    val seed = Random.nextString(10).replace(',', '!')
    seed + "," + Hasher("SHA-256").fromString(seed + password).head.toString
  }
}

@Singleton
class LocalAuthSrv @Inject()(db: Database, userSrv: UserSrv, localUserSrv: LocalUserSrv, implicit val mat: Materializer) extends AuthSrv {

  val name                                             = "local"
  override val capabilities: Set[AuthCapability.Value] = Set(AuthCapability.changePassword, AuthCapability.setPassword)
  lazy val logger                                      = Logger(getClass)

  private[services] def doAuthenticate(user: User, password: String): Boolean =
    user.password.map(_.split(",", 2)).fold(false) {
      case Array(seed, pwd) ⇒
        val hash = Hasher("SHA-256").fromString(seed + password).head.toString
        logger.trace(s"Authenticate user ${user.name} ($hash == $pwd)")
        hash == pwd
      case _ ⇒
        logger.trace(s"Authenticate user ${user.name} (no password in database)")
        false
    }

  override def authenticate(username: String, password: String)(implicit request: RequestHeader): Try[AuthContext] =
    db.tryTransaction { implicit graph ⇒
        userSrv
          .get(username)
          .getOrFail()
      }
      .filter(user ⇒ doAuthenticate(user, password))
      .map(user ⇒ localUserSrv.getFromId(request, user.login))
      .getOrElse(Failure(AuthenticationError("Authentication failure")))

  override def changePassword(username: String, oldPassword: String, newPassword: String)(implicit authContext: AuthContext): Try[Unit] =
    db.tryTransaction { implicit graph ⇒
        userSrv
          .get(username)
          .getOrFail()
      }
      .filter(user ⇒ doAuthenticate(user, oldPassword))
      .map(_ ⇒ setPassword(username, newPassword))
      .getOrElse(Failure(AuthorizationError("Authentication failure")))

  override def setPassword(username: String, newPassword: String)(implicit authContext: AuthContext): Try[Unit] =
    db.tryTransaction { implicit graph ⇒
      userSrv
        .get(username)
        .update("password" → Some(LocalAuthSrv.hashPassword(newPassword)))
        .map(_ ⇒ ())
    }
}
