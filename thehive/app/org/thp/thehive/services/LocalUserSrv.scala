package org.thp.thehive.services

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, User => ScalligraphUser, UserSrv => ScalligraphUserSrv}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.utils.Instance
import org.thp.scalligraph.{AuthenticationError, CreateError, NotFoundError}
import org.thp.thehive.models.{Organisation, Permissions, Profile, User}
import org.thp.thehive.services.UserOps._
import play.api.Configuration
import play.api.libs.json.JsObject
import play.api.mvc.RequestHeader

import scala.util.{Failure, Success, Try}

@Singleton
class LocalUserSrv @Inject() (
    @Named("with-thehive-schema") db: Database,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv,
    profileSrv: ProfileSrv,
    configuration: Configuration
) extends ScalligraphUserSrv {

  override def getAuthContext(request: RequestHeader, userId: String, organisationName: Option[String]): Try[AuthContext] =
    db.roTransaction { implicit graph =>
      val requestId = Instance.getRequestId(request)
      val userSteps = userSrv.get(userId)

      if (userSteps.clone().exists)
        userSteps
          .clone()
          .getAuthContext(requestId, organisationName)
          .headOption
          .orElse {
            organisationName.flatMap { org =>
              userSteps
                .getAuthContext(requestId, Organisation.administration.name)
                .headOption
                .map(authContext => authContext.changeOrganisation(org, authContext.permissions))
            }
          }
          .fold[Try[AuthContext]](Failure(AuthenticationError("Authentication failure")))(Success.apply)
      else Failure(NotFoundError(s"User $userId not found"))
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
          if orgaStr != Organisation.administration.name || profile.name == Profile.admin.name
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

  override def getSystemAuthContext: AuthContext = LocalUserSrv.getSystemAuthContext
}

object LocalUserSrv {
  def getSystemAuthContext: AuthContext =
    AuthContextImpl(
      User.system.login,
      User.system.name,
      Organisation.administration.name,
      Instance.getInternalId,
      Permissions.all
    )
}
