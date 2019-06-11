package org.thp.thehive.services

import java.util.Base64

import scala.util.{Failure, Random, Success, Try}

import play.api.mvc.RequestHeader

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.BadRequestError
import org.thp.scalligraph.auth.{AuthCapability, AuthContext, AuthSrv}
import org.thp.scalligraph.models.Database

@Singleton
class LocalKeyAuthSrv @Inject()(db: Database, userSrv: UserSrv, localUserSrv: LocalUserSrv) extends AuthSrv {
  override val name = "key"

  final protected def generateKey(): String = {
    val bytes = Array.ofDim[Byte](24)
    Random.nextBytes(bytes)
    Base64.getEncoder.encodeToString(bytes)
  }

  override val capabilities: Set[AuthCapability.Value] = Set(AuthCapability.authByKey)

  override def authenticate(key: String, organisation: Option[String])(implicit request: RequestHeader): Try[AuthContext] =
    db.tryTransaction { implicit graph ⇒
      userSrv
        .initSteps
        .getByAPIKey(key)
        .getOrFail()
        .flatMap(user ⇒ localUserSrv.getFromId(request, user.login, organisation))
    }

  override def renewKey(username: String)(implicit authContext: AuthContext): Try[String] =
    db.tryTransaction { implicit graph ⇒
      val newKey = generateKey()
      userSrv
        .get(username)
        .update("apikey" → Some(newKey))
        .map(_ ⇒ newKey)
    }

  override def getKey(username: String)(implicit authContext: AuthContext): Try[String] =
    db.tryTransaction { implicit graph ⇒
      userSrv
        .get(username)
        .getOrFail()
        .flatMap(_.apikey.fold[Try[String]](Failure(BadRequestError(s"User $username hasn't key")))(Success.apply))
    }

  override def removeKey(username: String)(implicit authContext: AuthContext): Try[Unit] =
    db.tryTransaction { implicit graph ⇒
      userSrv
        .get(username)
        .update("apikey" → None)
        .map(_ ⇒ ())
    }
}
