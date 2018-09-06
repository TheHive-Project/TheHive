package org.thp.thehive.services

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.{AuthenticationError, Instance, InternalError, NotFoundError}
import org.thp.thehive.models.{Permissions, User}
import play.api.mvc.RequestHeader

import scala.concurrent.Future
import scala.util.Try

@Singleton
class LocalUserSrv @Inject()(db: Database, userSrv: UserSrv) extends org.thp.scalligraph.auth.UserSrv {

  override def getFromId(request: RequestHeader, userId: String): Future[AuthContext] =
    db.transaction { implicit graph ⇒
      userSrv
        .get(userId)
        .headOption
        .map(user ⇒ getFromUser(request, user))
        .getOrElse(Future.failed[AuthContext](NotFoundError(s"User $userId not found")))
    }

  override def getFromUser(request: RequestHeader, user: org.thp.scalligraph.auth.User): Future[AuthContext] =
    user match {
      case u: User with Entity ⇒
        val organisationName = db.transaction { implicit graph ⇒
          userSrv.getOrganisation(u).name
        }
        Future.successful {
          new AuthContext() {
            override def userId: String               = u.login
            override def userName: String             = u.name
            override def organisation: String         = organisationName
            override def requestId: String            = Instance.getRequestId(request)
            override def permissions: Seq[Permission] = u.permissions
          }
        }
      case _ ⇒ Future.failed(InternalError(s"User has unexpected type: $user (${user.getClass})"))
    }

  override def getInitialUser(request: RequestHeader): Future[AuthContext] =
    db.transaction { implicit graph ⇒
      if (userSrv.count > 0)
        Future.failed(AuthenticationError(s"Use of initial user is forbidden because users exist in database"))
      else Future.successful(initialAuthContext)
    }

  override val initialAuthContext: AuthContext = new AuthContext {
    override def userId: String               = "system"
    override def userName: String             = "system"
    override def organisation: String         = "default"
    override def requestId: String            = Instance.getInternalId
    override def permissions: Seq[Permission] = Permissions.permissions
  }

  override def getUser(userId: String): Future[org.thp.scalligraph.auth.User] =
    db.transaction { implicit graph ⇒
      Future.fromTry(Try(userSrv.getOrFail(userId)))
    }
}
