package org.thp.thehive.services

import java.util.Base64

import javax.inject.{Inject, Named, Provider, Singleton}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.auth._
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import play.api.Configuration
import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Random, Success, Try}

class LocalKeyAuthSrv(
    @Named("with-thehive-schema") db: Database,
    userSrv: UserSrv,
    localUserSrv: LocalUserSrv,
    authSrv: AuthSrv,
    requestOrganisation: RequestOrganisation,
    ec: ExecutionContext
) extends KeyAuthSrv(authSrv, requestOrganisation, ec) {

  final protected def generateKey(): String = {
    val bytes = Array.ofDim[Byte](24)
    Random.nextBytes(bytes)
    Base64.getEncoder.encodeToString(bytes)
  }

  override val capabilities: Set[AuthCapability.Value] = Set(AuthCapability.authByKey)

  override def authenticate(key: String, organisation: Option[String])(implicit request: RequestHeader): Try[AuthContext] =
    db.roTransaction { implicit graph =>
      userSrv
        .initSteps
        .getByAPIKey(key)
        .getOrFail()
        .flatMap(user => localUserSrv.getAuthContext(request, user.login, organisation))
    }

  override def renewKey(username: String)(implicit authContext: AuthContext): Try[String] =
    db.tryTransaction { implicit graph =>
      val newKey = generateKey()
      userSrv
        .get(username)
        .update("apikey" -> Some(newKey))
        .map(_ => newKey)
    }

  override def getKey(username: String)(implicit authContext: AuthContext): Try[String] =
    db.roTransaction { implicit graph =>
      userSrv
        .getOrFail(username)
        .flatMap(_.apikey.fold[Try[String]](Failure(NotFoundError(s"User $username hasn't key")))(Success.apply))
    }

  override def removeKey(username: String)(implicit authContext: AuthContext): Try[Unit] =
    db.tryTransaction { implicit graph =>
      userSrv
        .get(username)
        .update("apikey" -> None)
        .map(_ => ())
    }
}

@Singleton
class LocalKeyAuthProvider @Inject() (
    @Named("with-thehive-schema") db: Database,
    userSrv: UserSrv,
    localUserSrv: LocalUserSrv,
    authSrvProvider: Provider[AuthSrv],
    requestOrganisation: RequestOrganisation,
    ec: ExecutionContext
) extends AuthSrvProvider {
  lazy val authSrv: AuthSrv = authSrvProvider.get
  override val name: String = "key"
  override def apply(config: Configuration): Try[AuthSrv] =
    Success(new LocalKeyAuthSrv(db, userSrv, localUserSrv, authSrv, requestOrganisation, ec))
}
