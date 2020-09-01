package org.thp.thehive.services

import io.github.nremond.SecureHash
import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.auth.{AuthCapability, AuthContext, AuthSrv, AuthSrvProvider}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.utils.Hasher
import org.thp.scalligraph.{AuthenticationError, AuthorizationError}
import org.thp.thehive.models.User
import play.api.mvc.RequestHeader
import play.api.{Configuration, Logger}

import scala.util.{Failure, Success, Try}

object LocalPasswordAuthSrv {

  def hashPassword(password: String): String =
    SecureHash.createHash(password)
}

class LocalPasswordAuthSrv(@Named("with-thehive-schema") db: Database, userSrv: UserSrv, localUserSrv: LocalUserSrv) extends AuthSrv {
  val name                                             = "local"
  override val capabilities: Set[AuthCapability.Value] = Set(AuthCapability.changePassword, AuthCapability.setPassword)
  lazy val logger: Logger                              = Logger(getClass)
  import LocalPasswordAuthSrv._

  def isValidPasswordLegacy(hash: String, password: String): Boolean =
    hash.split(",", 2) match {
      case Array(seed, pwd) =>
        val hash = Hasher("SHA-256").fromString(seed + password).head.toString
        logger.trace(s"Legacy password authentication check  ($hash == $pwd)")
        hash == pwd
      case _ =>
        logger.trace("Legacy password authenticate, invalid password format")
        false
    }

  def isValidPassword(user: User, password: String): Boolean =
    user.password.fold(false)(hash => SecureHash.validatePassword(password, hash) || isValidPasswordLegacy(hash, password))

  override def authenticate(username: String, password: String, organisation: Option[String], code: Option[String])(
      implicit request: RequestHeader
  ): Try[AuthContext] =
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
        .update(_.password, Some(hashPassword(newPassword)))
        .getOrFail("User")
        .map(_ => ())
    }
}

@Singleton
class LocalPasswordAuthProvider @Inject() (@Named("with-thehive-schema") db: Database, userSrv: UserSrv, localUserSrv: LocalUserSrv)
    extends AuthSrvProvider {
  override val name: String                               = "local"
  override def apply(config: Configuration): Try[AuthSrv] = Success(new LocalPasswordAuthSrv(db, userSrv, localUserSrv))
}
