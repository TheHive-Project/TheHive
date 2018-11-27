package org.thp.thehive.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import play.api.mvc.RequestHeader
import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.{AuthCapability, AuthContext, AuthSrv}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.{AuthenticationError, AuthorizationError, Hasher}
import org.thp.thehive.models.User
import play.api.Logger

@Singleton
class LocalAuthSrv @Inject()(
    db: Database,
    userSrv: UserSrv,
    localUserSrv: LocalUserSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer)
    extends AuthSrv {

  val name                  = "local"
  override val capabilities = Set(AuthCapability.changePassword, AuthCapability.setPassword)
  lazy val logger           = Logger(getClass)

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

  override def authenticate(username: String, password: String)(implicit request: RequestHeader, ec: ExecutionContext): Future[AuthContext] =
    db.transaction { implicit graph ⇒
      userSrv
        .get(username)
        .headOption()
        .filter(user ⇒ doAuthenticate(user, password))
        .map(user ⇒ localUserSrv.getFromUser(request, user))
        .getOrElse(Future.failed(AuthenticationError("Authentication failure")))
    }

  override def changePassword(username: String, oldPassword: String, newPassword: String)(
      implicit authContext: AuthContext,
      ec: ExecutionContext): Future[Unit] =
    db.transaction { implicit graph ⇒
      userSrv
        .get(username)
        .headOption()
        .filter(user ⇒ doAuthenticate(user, oldPassword))
        .map(_ ⇒ setPassword(username, newPassword))
        .getOrElse(Future.failed(AuthorizationError("Authentication failure")))
    }

  override def setPassword(username: String, newPassword: String)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Unit] =
    db.transaction { implicit graph ⇒
      val seed    = Random.nextString(10).replace(',', '!')
      val newHash = seed + "," + Hasher("SHA-256").fromString(seed + newPassword).head.toString
      userSrv.update(username, "password", Some(newHash))
      Future.successful(())
    }
}
