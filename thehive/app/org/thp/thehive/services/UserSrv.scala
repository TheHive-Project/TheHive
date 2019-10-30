package org.thp.thehive.services

import java.util.regex.Pattern

import scala.util.{Failure, Success, Try}

import play.api.Configuration
import play.api.libs.json.JsObject

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.scalligraph.{BadRequestError, EntitySteps, RichOptionTry}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._

object UserSrv {
  val initPassword: String = "secret"

  val init: User = User(
    login = "admin@thehive.local",
    name = "Default admin user",
    apikey = None,
    locked = false,
    password = Some(LocalPasswordAuthSrv.hashPassword(UserSrv.initPassword))
  )

  val system: User = User(login = "system@thehive.local", name = "TheHive system user", apikey = None, locked = false, password = None)
}

@Singleton
class UserSrv @Inject()(configuration: Configuration, roleSrv: RoleSrv, auditSrv: AuditSrv, attachmentSrv: AttachmentSrv, implicit val db: Database)
    extends VertexSrv[User, UserSteps] {

  override val initialValues: Seq[User] = Seq(UserSrv.init, UserSrv.system)
  val defaultUserDomain: Option[String] = configuration.getOptional[String]("auth.defaultUserDomain")
  val fullUserNameRegex: Pattern        = "[\\p{Graph}&&[^@.]](?:[\\p{Graph}&&[^@]]*)*@\\p{Alnum}+(?:[\\p{Alnum}-.])*".r.pattern

  val userAttachmentSrv                                                           = new EdgeSrv[UserAttachment, User, Attachment]
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): UserSteps = new UserSteps(raw)

  def checkUser(user: User): Try[User] = {
    val login =
      if (!user.login.contains('@') && defaultUserDomain.isDefined) s"${user.login}@${defaultUserDomain.get}"
      else user.login

    if (fullUserNameRegex.matcher(login).matches() && login != "system@thehive.local") Success(user.copy(login = login))
    else Failure(BadRequestError(s"User login is invalid, it must be an email address (found: ${user.login})"))
  }

  def create(user: User, avatar: Option[FFile], organisation: Organisation with Entity, profile: Profile with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichUser] =
    for {
      validUser   <- checkUser(user)
      createdUser <- createEntity(validUser)
      avatarId    <- avatar.map(setAvatar(createdUser, _)).flip
      _           <- roleSrv.create(createdUser, organisation, profile)
      richUser = RichUser(createdUser, avatarId, profile.name, profile.permissions, organisation.name)
      _ <- auditSrv.user.create(createdUser, richUser.toJson)
    } yield richUser

  def current(implicit graph: Graph, authContext: AuthContext): UserSteps = get(authContext.userId)

  override def get(idOrName: String)(implicit graph: Graph): UserSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else
      defaultUserDomain.fold(initSteps.getByName(idOrName)) {
        case d if !idOrName.contains('@') => initSteps.getByName(s"$idOrName@$d")
        case _                            => initSteps.getByName(idOrName)
      }

  override def update(
      steps: UserSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(UserSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (userSteps, updatedFields) =>
        userSteps
          .filterNot(_.systemUser)
          .newInstance()
          .getOrFail()
          .flatMap(auditSrv.user.update(_, updatedFields))
    }

  def setAvatar(user: User with Entity, avatar: FFile)(implicit graph: Graph, authContext: AuthContext): Try[String] =
    attachmentSrv.create(avatar).flatMap(setAvatar(user, _))

  def setAvatar(user: User with Entity, avatar: Attachment with Entity)(implicit graph: Graph, authContext: AuthContext): Try[String] = {
    unsetAvatar(user)
    userAttachmentSrv.create(UserAttachment(), user, avatar).map(_ => avatar.attachmentId)
  }

  def unsetAvatar(user: User with Entity)(implicit graph: Graph): Unit = get(user).avatar.remove()

  def setProfile(user: User with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      role <- get(user).role.filter(_.organisation.get(organisation)).getOrFail()
      _ = roleSrv.updateProfile(role, profile)
      _ <- auditSrv.user.changeProfile(user, organisation, profile)
    } yield ()
}

@EntitySteps[User]
class UserSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[User](raw) {
  def current(authContext: AuthContext): UserSteps = get(authContext.userId)

  def get(idOrName: String): UserSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(login: String): UserSteps = new UserSteps(raw.has(Key("login") of login))

  def visible(implicit authContext: AuthContext): UserSteps =
    if (authContext.permissions.contains(Permissions.manageOrganisation)) this
    else
      this.filter(_.or(_.organisations.get(authContext.organisation), _.systemUser))

  override def newInstance(newRaw: GremlinScala[Vertex]): UserSteps = new UserSteps(newRaw)
  override def newInstance(): UserSteps                             = new UserSteps(raw.clone())

  def can(requiredPermission: Permission)(implicit authContext: AuthContext): UserSteps =
    this.filter(_.organisations(requiredPermission).get(authContext.organisation))

  def getByAPIKey(key: String): UserSteps = new UserSteps(raw.has(Key("apikey") of key))

  def organisations: OrganisationSteps = new OrganisationSteps(raw.outTo[UserRole].outTo[RoleOrganisation])

  private def organisations0(requiredPermission: String): OrganisationSteps =
    new OrganisationSteps(
      raw
        .outTo[UserRole]
        .filter(_.outTo[RoleProfile].has(Key("permissions") of requiredPermission))
        .outTo[RoleOrganisation]
    )

  def organisations(requiredPermission: String): OrganisationSteps = {
    val isInDefaultOrganisation = newInstance().organisations0(requiredPermission).get(OrganisationSrv.administration.name).exists()
    if (isInDefaultOrganisation) new OrganisationSteps(db.labelFilter(Model.vertex[Organisation])(graph.V))
    else organisations0(requiredPermission)
  }

  def config: ConfigSteps = new ConfigSteps(raw.outTo[UserConfig])

  def getAuthContext(requestId: String, organisation: Option[String]): Traversal[AuthContext, AuthContext] = {
    val organisationName = organisation
      .orElse(
        this
          .newInstance()
          .outTo[UserRole]
          .outTo[RoleOrganisation]
          .value[String](Key("name"))
          .headOption()
      )
      .getOrElse(OrganisationSrv.administration.name)
    getAuthContext(requestId, organisationName)
  }

  def getAuthContext(requestId: String, organisationName: String): Traversal[AuthContext, AuthContext] =
    Traversal(
      this
        .filter(_.organisations.get(organisationName))
        .raw
        .has(Key("locked") of false)
        .project(
          _.apply(By(__.value[String]("login")))
            .and(By(__.value[String]("name")))
            .and(By(__[Vertex].outTo[UserRole].filter(_.outTo[RoleOrganisation].has(Key("name") of organisationName)).outTo[RoleProfile]))
        )
        .map {
          case (userId, userName, profile) =>
            val permissions =
              if (organisationName == OrganisationSrv.administration.name) profile.as[Profile].permissions
              else profile.as[Profile].permissions -- Permissions.restrictedPermissions
            AuthContextImpl(userId, userName, organisationName, requestId, permissions)
        }
    )

  def richUser(organisation: String): Traversal[RichUser, RichUser] =
    this
      .project(
        _.apply(By[Vertex]())
          .and(By(__[Vertex].outTo[UserRole].filter(_.outTo[RoleOrganisation].has(Key("name") of organisation)).outTo[RoleProfile].fold()))
          .and(By(__[Vertex].outTo[UserAttachment].fold()))
      )
      .collect {
        case (user, profiles, attachment) if profiles.size() == 1 =>
          val profile = profiles.get(0).as[Profile]
          val avatar  = atMostOneOf[Vertex](attachment).map(_.as[Attachment].attachmentId)
          RichUser(user.as[User], avatar, profile.name, profile.permissions, organisation)
      }

  def role: RoleSteps = new RoleSteps(raw.outTo[UserRole])

  def avatar: AttachmentSteps = new AttachmentSteps(raw.outTo[UserAttachment])

  def systemUser: UserSteps = this.has(Key[String]("login"), P.eq[String](UserSrv.system.login))
}
