package org.thp.thehive.services

import io.github.nremond.SecureHash
import org.thp.scalligraph.auth.{AuthCapability, AuthContext, AuthSrv, AuthSrvProvider}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.utils.Hasher
import org.thp.scalligraph.{AuthenticationError, AuthorizationError, EntityIdOrName}
import org.thp.thehive.models.User
import play.api.mvc.RequestHeader
import play.api.{Configuration, Logger}

import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object LocalPasswordAuthSrv {

  def hashPassword(password: String): String =
    SecureHash.createHash(password)
}

class LocalPasswordAuthSrv(db: Database, userSrv: UserSrv, localUserSrv: LocalUserSrv, maxAttempts: Option[Int], resetAfter: Option[Duration])
    extends AuthSrv {
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

  private def timeElapsed(user: User with Entity): Boolean =
    user.lastFailed.fold(true)(lf => resetAfter.fold(false)(ra => (System.currentTimeMillis - lf.getTime) > ra.toMillis))

  private def maxAttemptsReached(user: User with Entity) =
    (for {
      ma <- maxAttempts
      fa <- user.failedAttempts
    } yield fa >= ma).getOrElse(false)

  def isValidPassword(user: User with Entity, password: String): Boolean =
    if (!maxAttemptsReached(user) || timeElapsed(user)) {
      val isValid = user.password.fold(false)(hash => SecureHash.validatePassword(password, hash) || isValidPasswordLegacy(hash, password))
      if (!isValid)
        db.tryTransaction { implicit graph =>
          userSrv
            .get(user)
            .update(_.failedAttempts, Some(user.failedAttempts.fold(1)(_ + 1)))
            .update(_.lastFailed, Some(new Date))
            .getOrFail("User")
        }
      else if (user.failedAttempts.exists(_ > 0))
        db.tryTransaction { implicit graph =>
          userSrv
            .get(user)
            .update(_.failedAttempts, Some(0))
            .getOrFail("User")
        }
      isValid
    } else {
      logger.warn(
        s"Authentication of ${user.login} is refused because the max attempts is reached (${user.failedAttempts.orNull}/${maxAttempts.orNull})"
      )
      false
    }

  def resetFailedAttempts(user: User with Entity)(implicit graph: Graph): Try[Unit] =
    userSrv.get(user).update(_.failedAttempts, None).update(_.lastFailed, None).getOrFail("User").map(_ => ())

  override def authenticate(username: String, password: String, organisation: Option[EntityIdOrName], code: Option[String])(implicit
      request: RequestHeader
  ): Try[AuthContext] =
    db.roTransaction { implicit graph =>
      userSrv
        .getOrFail(EntityIdOrName(username))
    }.filter(user => isValidPassword(user, password))
      .map(user => localUserSrv.getAuthContext(request, user.login, organisation))
      .getOrElse(Failure(AuthenticationError("Authentication failure")))

  override def changePassword(username: String, oldPassword: String, newPassword: String)(implicit authContext: AuthContext): Try[Unit] =
    db.roTransaction { implicit graph =>
      userSrv
        .getOrFail(EntityIdOrName(username))
    }.filter(user => isValidPassword(user, oldPassword))
      .map(_ => setPassword(username, newPassword))
      .getOrElse(Failure(AuthorizationError("Authentication failure")))

  override def setPassword(username: String, newPassword: String)(implicit authContext: AuthContext): Try[Unit] =
    db.tryTransaction { implicit graph =>
      userSrv
        .get(EntityIdOrName(username))
        .update(_.password, Some(hashPassword(newPassword)))
        .update(_._updatedAt, Some(new Date))
        .update(_._updatedBy, Some(authContext.userId))
        .getOrFail("User")
        .map(_ => ())
    }
}

@Singleton
class LocalPasswordAuthProvider @Inject() (db: Database, userSrv: UserSrv, localUserSrv: LocalUserSrv) extends AuthSrvProvider {
  override val name: String = "local"
  override def apply(config: Configuration): Try[AuthSrv] = {
    val maxAttempts = config.getOptional[Int]("maxAttempts")
    val resetAfter  = config.getOptional[Duration]("resetAfter")
    Success(new LocalPasswordAuthSrv(db, userSrv, localUserSrv, maxAttempts, resetAfter))
  }
}
