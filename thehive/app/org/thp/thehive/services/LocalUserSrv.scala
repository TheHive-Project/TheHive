package org.thp.thehive.services

import scala.util.Try

import play.api.mvc.RequestHeader

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, UserSrv => ScalligraphUserSrv}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.AuthenticationError
import org.thp.scalligraph.utils.Instance
import org.thp.thehive.models.Permissions

@Singleton
class LocalUserSrv @Inject()(db: Database, userSrv: UserSrv) extends ScalligraphUserSrv {

  override def getFromId(request: RequestHeader, userId: String, organisationName: Option[String]): Try[AuthContext] =
    db.roTransaction { implicit graph =>
      userSrv
        .initSteps
        .get(userId)
        .getAuthContext(Instance.getRequestId(request), organisationName)
        .orFail(AuthenticationError("Authentication failure"))
    }

  override def getSystemAuthContext: AuthContext =
    AuthContextImpl(UserSrv.initUser.login, UserSrv.initUser.name, OrganisationSrv.default.name, Instance.getInternalId, Permissions.all)
}
