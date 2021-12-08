package org.thp.thehive.services

import org.apache.commons.codec.binary.Base32
import org.thp.scalligraph.auth.{UserSrv => _, _}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.{Graph, TraversalOps}
import org.thp.scalligraph.{AuthenticationError, EntityIdOrName, MultiFactorCodeRequired, ScalligraphApplication}
import org.thp.thehive.TheHiveModule
import play.api.Configuration
import play.api.mvc.RequestHeader

import java.net.URI
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.util.{Failure, Random, Success, Try}

class TOTPAuthSrv(
    configuration: Configuration,
    appConfig: ApplicationConfig,
    availableAuthProviders: Seq[AuthSrvProvider],
    userSrv: UserSrv,
    db: Database
) extends MultiAuthSrv(configuration, appConfig, availableAuthProviders)
    with TraversalOps {
  override val name: String = "totp"

  val enabledConfig: ConfigItem[Boolean, Boolean] = appConfig.item[Boolean]("auth.multifactor.enabled", "multifactor activation")
  def enabled: Boolean                            = enabledConfig.get

  val issuerNameConfig: ConfigItem[String, String] = appConfig.item[String]("auth.multifactor.issuer", "name of the multifactor issuer")
  def issuerName: String                           = issuerNameConfig.get

  override def capabilities: Set[AuthCapability.Value] = if (enabled) super.capabilities + AuthCapability.mfa else super.capabilities

  def codeIsValid(secret: String, code: Int): Boolean = {
    val key     = new Base32().decode(secret)
    val signKey = new SecretKeySpec(key, "HmacSHA1")
    val mac     = Mac.getInstance("HmacSHA1")
    mac.init(signKey)
    val timestamp = System.currentTimeMillis() / TimeUnit.SECONDS.toMillis(30)
    (timestamp - 1 to timestamp + 1).exists { ts =>
      val data   = (56 to 0 by -8).map(i => (ts >> i).toByte).toArray
      val hash   = mac.doFinal(data)
      val offset = hash(hash.length - 1) & 0xf
      (BigInt(hash.slice(offset, offset + 4)).toInt & 0x7fffffff) % 1000000 == code
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

  override def authenticate(username: String, password: String, organisation: Option[EntityIdOrName], code: Option[String])(implicit
      request: RequestHeader
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
    userSrv.get(EntityIdOrName(username)).headOption.flatMap(_.totpSecret)

  def unsetSecret(username: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    userSrv
      .get(EntityIdOrName(username))
      .update(_.totpSecret, None)
      .update(_._updatedAt, Some(new Date))
      .update(_._updatedBy, Some(authContext.userId))
      .domainMap(_ => ())
      .getOrFail("User")

  def generateSecret(): String = {
    val key = Array.ofDim[Byte](20)
    Random.nextBytes(key)
    new Base32().encodeToString(key)
  }

  def setSecret(username: String)(implicit graph: Graph, authContext: AuthContext): Try[String] =
    setSecret(username, generateSecret())

  def setSecret(username: String, secret: String)(implicit graph: Graph, authContext: AuthContext): Try[String] =
    userSrv
      .get(EntityIdOrName(username))
      .update(_.totpSecret, Some(secret))
      .update(_._updatedAt, Some(new Date))
      .update(_._updatedBy, Some(authContext.userId))
      .domainMap(_ => secret)
      .getOrFail("User")

  def getSecretURI(username: String, secret: String): URI =
    new URI("otpauth", "totp", s"/TheHive:$username", s"secret=$secret&issuer=$issuerName", null)
}

class TOTPAuthSrvProvider(
    appConfig: ApplicationConfig,
    authProviders: Seq[AuthSrvProvider],
    userSrv: UserSrv,
    db: Database
) extends AuthSrvProvider {
  def this(app: ScalligraphApplication, thehiveModule: TheHiveModule) =
    this(app.applicationConfig, app.authSrvProviders, thehiveModule.userSrv, app.database)

  def this(app: ScalligraphApplication) = this(app, app.getModule[TheHiveModule])

  override val name: String = "TOTP"

  override def apply(configuration: Configuration): Try[AuthSrv] = Try(new TOTPAuthSrv(configuration, appConfig, authProviders, userSrv, db))
}
