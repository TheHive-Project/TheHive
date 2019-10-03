package org.thp.thehive.services

import scala.util.{Failure, Random, Success, Try}

import play.api.mvc.RequestHeader
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.{AuthCapability, AuthContext, AuthSrv, AuthSrvProvider}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.utils.Hasher
import org.thp.scalligraph.{AuthenticationError, AuthorizationError}
import org.thp.thehive.models.User

object LocalPasswordAuthSrv {

  def hashPassword(password: String): String = {
    val seed = Random.nextString(10).replace(',', '!')
    seed + "," + Hasher("SHA-256").fromString(seed + password).head.toString
  }
}

class LocalPasswordAuthSrv(db: Database, userSrv: UserSrv, localUserSrv: LocalUserSrv) extends AuthSrv {
  val name                                             = "local"
  override val capabilities: Set[AuthCapability.Value] = Set(AuthCapability.changePassword, AuthCapability.setPassword)
  lazy val logger                                      = Logger(getClass)
  import LocalPasswordAuthSrv._

  def isValidPassword(user: User, password: String): Boolean =
    user.password.map(_.split(",", 2)).fold(false) {
      case Array(seed, pwd) =>
        val hash = Hasher("SHA-256").fromString(seed + password).head.toString
        logger.trace(s"Authenticate user ${user.name} ($hash == $pwd)")
        hash == pwd
      case _ =>
        logger.trace(s"Authenticate user ${user.name} (no password in database)")
        false
    }

  override def authenticate(username: String, password: String, organisation: Option[String])(implicit request: RequestHeader): Try[AuthContext] =
    db.roTransaction { implicit graph =>
        userSrv
          .getOrFail(username)
      }
      .filter(user => isValidPassword(user, password))
      .map(user => localUserSrv.getAuthContext(request, user.login, organisation))
      .getOrElse(Failure(AuthenticationError("Authentication failure")))

  override def changePassword(username: String, oldPassword: String, newPassword: String)(implicit authContext: AuthContext): Try[Unit] =
    db.roTransaction { implicit graph =>
        userSrv
          .getOrFail(username)
      }
      .filter(user => isValidPassword(user, oldPassword))
      .map(_ => setPassword(username, newPassword))
      .getOrElse(Failure(AuthorizationError("Authentication failure")))

  override def setPassword(username: String, newPassword: String)(implicit authContext: AuthContext): Try[Unit] =
    db.tryTransaction { implicit graph =>
      userSrv
        .get(username)
        .update("password" -> Some(hashPassword(newPassword)))
        .map(_ => ())
    }
}

@Singleton
class LocalPasswordAuthProvider @Inject()(db: Database, userSrv: UserSrv, localUserSrv: LocalUserSrv) extends AuthSrvProvider {
  override val name: String                               = "local"
  override def apply(config: Configuration): Try[AuthSrv] = Success(new LocalPasswordAuthSrv(db, userSrv, localUserSrv))
}
