package org.thp.thehive.services

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, User => ScalligraphUser, UserSrv => ScalligraphUserSrv}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.utils.Instance
import org.thp.scalligraph.{AuthenticationError, CreateError, NotFoundError}
import org.thp.thehive.models.{Permissions, User}
import play.api.Configuration
import play.api.libs.json.JsObject
import play.api.mvc.RequestHeader

import scala.util.{Failure, Success, Try}

@Singleton
class LocalUserSrv @Inject() (db: Database, userSrv: UserSrv, organisationSrv: OrganisationSrv, profileSrv: ProfileSrv, configuration: Configuration)
    extends ScalligraphUserSrv {

  override def getAuthContext(request: RequestHeader, userId: String, organisationName: Option[String]): Try[AuthContext] =
    db.roTransaction { implicit graph =>
      val requestId = Instance.getRequestId(request)
      val userSteps = userSrv.get(userId)

      if (userSteps.newInstance().exists()) {
        userSteps
          .newInstance()
          .getAuthContext(requestId, organisationName)
          .headOption()
          .orElse {
            organisationName.flatMap { org =>
              userSteps
                .getAuthContext(requestId, OrganisationSrv.administration.name)
                .headOption()
                .map(_.changeOrganisation(org))
            }
          }
          .fold[Try[AuthContext]](Failure(AuthenticationError("Authentication failure")))(Success.apply)
      } else Failure(NotFoundError(s"User $userId not found"))
    }

  override def createUser(userId: String, userInfo: JsObject): Try[ScalligraphUser] = {
    val autocreate = configuration.getOptional[Boolean]("user.autoCreateOnSso").getOrElse(false)
    if (autocreate) {
      val profileFieldName      = configuration.getOptional[String]("user.profileFieldName")
      val organisationFieldName = configuration.getOptional[String]("user.organisationFieldName")
      val defaultProfile        = configuration.getOptional[String]("user.defaults.profile")
      val defaultOrg            = configuration.getOptional[String]("user.defaults.organisation")
      def readData(json: JsObject, field: Option[String], default: Option[String]): Try[String] =
        Try((json \ field.get).as[String]).orElse(Try(default.get))

      db.tryTransaction { implicit graph =>
        implicit val defaultAuthContext: AuthContext = getSystemAuthContext
        for {
          profileStr <- readData(userInfo, profileFieldName, defaultProfile)
          profile    <- profileSrv.getOrFail(profileStr)
          orgaStr    <- readData(userInfo, organisationFieldName, defaultOrg)
          if orgaStr != OrganisationSrv.administration.name || profile.name == ProfileSrv.admin.name
          organisation <- organisationSrv.getOrFail(orgaStr)
          richUser <- userSrv.addOrCreateUser(
            User(userId, userId, None, locked = false, None, None),
            None,
            organisation,
            profile
          )
        } yield richUser.user
      }
    } else Failure(CreateError(s"Autocreate on single sign on is $autocreate"))
  }

  override def getSystemAuthContext: AuthContext =
    AuthContextImpl(UserSrv.system.login, UserSrv.system.name, OrganisationSrv.administration.name, Instance.getInternalId, Permissions.all)
}
