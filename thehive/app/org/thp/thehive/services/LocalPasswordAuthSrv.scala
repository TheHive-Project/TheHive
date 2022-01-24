package org.thp.thehive.services

import io.github.nremond.SecureHash
import org.thp.scalligraph.auth.{AuthCapability, AuthContext, AuthSrv, AuthSrvProvider}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.utils.Hasher
import org.thp.scalligraph.{AuthenticationError, AuthorizationError, BadRequestError, EntityIdOrName}
import org.thp.thehive.models.User
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.RequestHeader
import play.api.{Configuration, Logger}

import java.util.Date
import scala.concurrent.duration.Duration
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

class LocalPasswordAuthSrv(
    db: Database,
    userSrv: UserSrv,
    localUserSrv: LocalUserSrv,
    config: Configuration,
    maxAttempts: Option[Int],
    resetAfter: Option[Duration]
) extends AuthSrv
    with TheHiveOpsNoDeps {
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

  def timeElapsed(user: User with Entity): Boolean =
    user.lastFailed.fold(true)(lf => resetAfter.fold(false)(ra => (System.currentTimeMillis - lf.getTime) > ra.toMillis))

  def lockedUntil(user: User with Entity): Option[Date] =
    if (maxAttemptsReached(user))
      user.lastFailed.map { lf =>
        resetAfter.fold(new Date(Long.MaxValue))(ra => new Date(ra.toMillis + lf.getTime))
      }
    else None

  def maxAttemptsReached(user: User with Entity): Boolean =
    (for {
      ma <- maxAttempts
      fa <- user.failedAttempts
    } yield fa >= ma).getOrElse(false)

  def isValidPassword(user: User with Entity, password: String): Boolean =
    if (!maxAttemptsReached(user) || timeElapsed(user)) {
      val isValid = user.password.fold(false)(hash => SecureHash.validatePassword(password, hash) || isValidPasswordLegacy(hash, password))
      if (!isValid)
        db.tryTransaction { implicit graph =>
          userSrv
            .get(user)
            .update(_.failedAttempts, Some(user.failedAttempts.fold(1)(_ + 1)))
            .update(_.lastFailed, Some(new Date))
            .getOrFail("User")
        }
      else if (user.failedAttempts.exists(_ > 0))
        db.tryTransaction { implicit graph =>
          userSrv
            .get(user)
            .update(_.failedAttempts, Some(0))
            .getOrFail("User")
        }
      isValid
    } else {
      logger.warn(
        s"Authentication of ${user.login} is refused because the max attempts is reached (${user.failedAttempts.orNull}/${maxAttempts.orNull})"
      )
      false
    }

  def resetFailedAttempts(user: User with Entity)(implicit graph: Graph): Try[Unit] =
    userSrv.get(user).update(_.failedAttempts, None).update(_.lastFailed, None).getOrFail("User").map(_ => ())

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
          .update(_._updatedAt, Some(new Date))
          .update(_._updatedBy, Some(authContext.userId))
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
  override val name: String = "local"
  override def apply(config: Configuration): Try[AuthSrv] = {
    val maxAttempts = config.getOptional[Int]("maxAttempts")
    val resetAfter  = config.getOptional[Duration]("resetAfter")
    Success(new LocalPasswordAuthSrv(db, userSrv, localUserSrv, config, maxAttempts, resetAfter))
  }
}
