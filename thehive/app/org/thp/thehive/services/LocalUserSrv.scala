package org.thp.thehive.services

import scala.util.{Failure, Success, Try}

import play.api.mvc.RequestHeader

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.AuthenticationError
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, UserSrv => ScalligraphUserSrv}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.utils.Instance
import org.thp.thehive.models.Permissions

@Singleton
class LocalUserSrv @Inject()(db: Database, userSrv: UserSrv) extends ScalligraphUserSrv {

  override def getAuthContext(request: RequestHeader, userId: String, organisationName: Option[String]): Try[AuthContext] =
    db.roTransaction { implicit graph =>
      val requestId = Instance.getRequestId(request)
      val userSteps = userSrv.get(userId)

      userSteps
        .newInstance()
        .getAuthContext(requestId, organisationName)
        .headOption()
        .orElse {
          organisationName.flatMap { org =>
            userSteps
              .getAuthContext(requestId, OrganisationSrv.default.name)
              .headOption()
              .map(_.changeOrganisation(org))
          }
        }
        .fold[Try[AuthContext]](Failure(AuthenticationError("Authentication failure")))(Success.apply)
    }

  override def getSystemAuthContext: AuthContext =
    AuthContextImpl(UserSrv.initUser.login, UserSrv.initUser.name, OrganisationSrv.default.name, Instance.getInternalId, Permissions.all)
}
