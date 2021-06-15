package org.thp.thehive.services

import io.github.nremond.SecureHash
import org.thp.scalligraph.auth.{AuthCapability, AuthContext, AuthSrv, AuthSrvProvider}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.utils.Hasher
import org.thp.scalligraph.{AuthenticationError, AuthorizationError, BadRequestError, EntityIdOrName}
import org.thp.thehive.models.User
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.RequestHeader
import play.api.{Configuration, Logger}

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.{Failure, Success, Try}

object LocalPasswordAuthSrv {

  def hashPassword(password: String): String =
    SecureHash.createHash(password)

  case class PasswordPolicyConfig(
      enabled: Boolean,
      minLength: Option[Int],
      minLowerCase: Option[Int],
      minUpperCase: Option[Int],
      minDigit: Option[Int],
      minSpecial: Option[Int],
      cannotContainUsername: Option[Boolean]
  )

  object PasswordPolicyConfig {
    implicit val writes: OWrites[PasswordPolicyConfig] = Json.writes[PasswordPolicyConfig]
  }
}

class LocalPasswordAuthSrv(db: Database, userSrv: UserSrv, localUserSrv: LocalUserSrv, config: Configuration) extends AuthSrv with TheHiveOpsNoDeps {
  val name                                             = "local"
  override val capabilities: Set[AuthCapability.Value] = Set(AuthCapability.changePassword, AuthCapability.setPassword)
  lazy val logger: Logger                              = Logger(getClass)
  import LocalPasswordAuthSrv._

  def isValidPasswordLegacy(hash: String, password: String): Boolean =
    hash.split(",", 2) match {
      case Array(seed, pwd) =>
        val hash = Hasher("SHA-256").fromString(seed + password).head.toString
        logger.trace(s"Legacy password authentication check  ($hash == $pwd)")
        hash == pwd
      case _ =>
        logger.trace("Legacy password authenticate, invalid password format")
        false
    }

  def isValidPassword(user: User, password: String): Boolean =
    user.password.fold(false)(hash => SecureHash.validatePassword(password, hash) || isValidPasswordLegacy(hash, password))

  override def authenticate(username: String, password: String, organisation: Option[EntityIdOrName], code: Option[String])(implicit
      request: RequestHeader
  ): Try[AuthContext] =
    db.roTransaction { implicit graph =>
      userSrv
        .getOrFail(EntityIdOrName(username))
    }.filter(user => isValidPassword(user, password))
      .map(user => localUserSrv.getAuthContext(request, user.login, organisation))
      .getOrElse(Failure(AuthenticationError("Authentication failure")))

  override def changePassword(username: String, oldPassword: String, newPassword: String)(implicit authContext: AuthContext): Try[Unit] =
    db.roTransaction { implicit graph =>
      userSrv
        .getOrFail(EntityIdOrName(username))
    }.filter(user => isValidPassword(user, oldPassword))
      .map(_ => setPassword(username, newPassword))
      .getOrElse(Failure(AuthorizationError("Authentication failure")))

  override def setPassword(username: String, newPassword: String)(implicit authContext: AuthContext): Try[Unit] =
    for {
      _ <- checkPasswordPolicy(username, newPassword)
      _ <- db.tryTransaction { implicit graph =>
        userSrv
          .get(EntityIdOrName(username))
          .update(_.password, Some(hashPassword(newPassword)))
          .getOrFail("User")
      }
    } yield ()

  val passwordPolicyConfig: PasswordPolicyConfig = PasswordPolicyConfig(
    enabled = config.getOptional[Boolean]("passwordPolicy.enabled").getOrElse(false),
    minLength = config.getOptional[Int]("passwordPolicy.minLength"),
    minLowerCase = config.getOptional[Int]("passwordPolicy.minLowerCase"),
    minUpperCase = config.getOptional[Int]("passwordPolicy.minUpperCase"),
    minDigit = config.getOptional[Int]("passwordPolicy.minDigit"),
    minSpecial = config.getOptional[Int]("passwordPolicy.minSpecial"),
    cannotContainUsername = config.getOptional[Boolean]("passwordPolicy.cannotContainUsername")
  )

  private def checkPasswordPolicy(username: String, newPassword: String): Try[Unit] = {
    import org.passay._
    if (passwordPolicyConfig.enabled) {
      val rules: Seq[Rule] = Seq(
        passwordPolicyConfig.minLength.map(min => new LengthRule(min, Integer.MAX_VALUE)),
        passwordPolicyConfig.minLowerCase.map(min => new CharacterRule(EnglishCharacterData.LowerCase, min)),
        passwordPolicyConfig.minUpperCase.map(min => new CharacterRule(EnglishCharacterData.UpperCase, min)),
        passwordPolicyConfig.minDigit.map(min => new CharacterRule(EnglishCharacterData.Digit, min)),
        passwordPolicyConfig.minSpecial.map(min => new CharacterRule(EnglishCharacterData.Special, min)),
        if (passwordPolicyConfig.cannotContainUsername.getOrElse(false)) Some(new UsernameRule()) else None
      ).flatten
      logger.trace(s"Checking password policy with rules $rules")
      val passwordValidator = new PasswordValidator(rules: _*)
      val result            = passwordValidator.validate(new PasswordData(username, newPassword))
      if (result.isValid) Success(())
      else {
        val errorMessages = passwordValidator.getMessages(result)
        Failure(BadRequestError(s"New password does not meet password policy: ${errorMessages.asScala.mkString(" ")}"))
      }
    } else
      Success(())
  }
}

class LocalPasswordAuthProvider(db: Database, userSrv: UserSrv, localUserSrv: LocalUserSrv) extends AuthSrvProvider {
  override val name: String                               = "local"
  override def apply(config: Configuration): Try[AuthSrv] = Success(new LocalPasswordAuthSrv(db, userSrv, localUserSrv, config))
}
