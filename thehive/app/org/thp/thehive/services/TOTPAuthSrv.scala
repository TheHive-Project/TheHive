package org.thp.thehive.services

import java.net.URI
import java.util.concurrent.TimeUnit

import gremlin.scala.Graph
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.{Inject, Provider, Singleton}
import org.apache.commons.codec.binary.Base32
import org.thp.scalligraph.{AuthenticationError, MultiFactorCodeRequired}
import org.thp.scalligraph.auth.{AuthCapability, AuthContext, AuthSrv, AuthSrvProvider, MultiAuthSrv}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.steps.StepsOps._
import play.api.Configuration
import play.api.mvc.RequestHeader

import scala.collection.immutable
import scala.util.{Failure, Random, Success, Try}

class TOTPAuthSrv(
    configuration: Configuration,
    appConfig: ApplicationConfig,
    availableAuthProviders: immutable.Set[AuthSrvProvider],
    userSrv: UserSrv,
    db: Database
) extends MultiAuthSrv(configuration, appConfig, availableAuthProviders) {
  override val name: String = "totp"

  val enabledConfig: ConfigItem[Boolean, Boolean] = appConfig.item[Boolean]("auth.multifactor.enabled", "multifactor activation")
  def enabled: Boolean                            = enabledConfig.get

  val issuerNameConfig: ConfigItem[String, String] = appConfig.item[String]("auth.multifactor.issuer", "name of the multifactor issuer")
  def issuerName: String                           = issuerNameConfig.get

  override def capabilities: Set[AuthCapability.Value] = super.capabilities + AuthCapability.mfa

  def codeIsValid(secret: String, code: Int): Boolean = {
    val key     = new Base32().decode(secret)
    val signKey = new SecretKeySpec(key, "HmacSHA1")
    val mac     = Mac.getInstance("HmacSHA1")
    mac.init(signKey)
    val timestamp = System.currentTimeMillis() / TimeUnit.SECONDS.toMillis(30)
    (timestamp - 1 to timestamp + 1).exists { ts =>
      val data   = (56 to 0 by -8).map(i => (ts >> i).toByte).toArray
      val hash   = mac.doFinal(data)
      val offset = hash(hash.length - 1) & 0xF
      (BigInt(hash.slice(offset, offset + 4)).toInt & 0x7FFFFFFF) % 1000000 == code
    }
  }

  def checkCode(secret: String, code: Option[String]): Try[Unit] =
    code
      .flatMap(c => Try(c.toInt).toOption)
      .map {
        case intCode if codeIsValid(secret, intCode) => Success(())
        case _                                       => Failure(AuthenticationError("Invalid MFA code"))
      }
      .getOrElse(Failure(MultiFactorCodeRequired("MFA code is required")))

  override def authenticate(username: String, password: String, organisation: Option[String], code: Option[String])(
      implicit request: RequestHeader
  ): Try[AuthContext] =
    super.authenticate(username, password, organisation, code).flatMap {
      case authContext if !enabled => Success(authContext)
      case authContext =>
        db.roTransaction(implicit graph => getSecret(username)) match {
          case Some(secret) => checkCode(secret, code).map(_ => authContext)
          case None         => Success(authContext)
        }
    }

  def getSecret(username: String)(implicit graph: Graph): Option[String] =
    userSrv.get(username).headOption().flatMap(_.totpSecret)

  def unsetSecret(username: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    userSrv.get(username).update("totpSecret" -> None).map(_ => ())

  def generateSecret(): String = {
    val key = Array.ofDim[Byte](20)
    Random.nextBytes(key)
    new Base32().encodeToString(key)
  }

  def setSecret(username: String)(implicit graph: Graph, authContext: AuthContext): Try[String] =
    setSecret(username, generateSecret())

  def setSecret(username: String, secret: String)(implicit graph: Graph, authContext: AuthContext): Try[String] =
    userSrv
      .get(username)
      .update("totpSecret" -> Some(secret))
      .map(_ => secret)

  def getSecretURI(username: String, secret: String): URI =
    new URI("otauth", "totp", s"/TheHive:$username", s"secret=$secret&issuer=$issuerName", null)
}

@Singleton
class TOTPAuthSrvProvider @Inject() (
    configuration: Configuration,
    appConfig: ApplicationConfig,
    authProviders: immutable.Set[AuthSrvProvider],
    userSrv: UserSrv,
    db: Database
) extends Provider[AuthSrv] {
  override def get(): AuthSrv = new TOTPAuthSrv(configuration, appConfig, authProviders, userSrv, db)
}
