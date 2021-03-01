package services

import javax.inject.{Inject, Singleton}

import scala.collection.immutable
import scala.concurrent.ExecutionContext

import play.api.{Configuration, Logger}

import org.elastic4play.services.AuthSrv
import org.elastic4play.services.auth.MultiAuthSrv

object TheHiveAuthSrv {
  private[TheHiveAuthSrv] lazy val logger = Logger(getClass)

  def getAuthSrv(authTypes: Seq[String], authModules: immutable.Set[AuthSrv]): Seq[AuthSrv] =
    ("key" +: authTypes.filterNot(_ == "key"))
      .flatMap { authType =>
        authModules
          .find(_.name == authType)
          .orElse {
            logger.error(s"Authentication module $authType not found")
            None
          }
      }
}

@Singleton
class TheHiveAuthSrv @Inject()(
    configuration: Configuration,
    authModules: immutable.Set[AuthSrv],
    implicit override val ec: ExecutionContext
) extends MultiAuthSrv(
      TheHiveAuthSrv.getAuthSrv(configuration.getDeprecated[Option[Seq[String]]]("auth.provider", "auth.type").getOrElse(Seq("local")), authModules),
      ec
    ) {

  // Uncomment the following lines if you want to prevent user with key to use password to authenticate
  //  override def authenticate(username: String, password: String)(implicit request: RequestHeader): Future[AuthContext] =
  //    userSrv.get(username)
  //      .transformWith {
  //        case Success(user) if user.key().isDefined ⇒ Future.failed(AuthenticationError("Authentication by password is not permitted for user with key"))
  //        case _: Success[_]                         ⇒ super.authenticate(username, password)
  //        case _: Failure[_]                         ⇒ Future.failed(AuthenticationError("Authentication failure"))
  //      }
}
