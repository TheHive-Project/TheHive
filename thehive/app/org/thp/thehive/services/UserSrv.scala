package org.thp.thehive.services

import java.util.regex.Pattern
import java.util.{List => JList, Map => JMap}

import akka.actor.ActorRef
import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.apache.tinkerpop.gremlin.structure.{Graph, Vertex}
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.Converter.CList
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.scalligraph.{AuthorizationError, BadRequestError, InternalError, RichOptionTry}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ProfileOps._
import org.thp.thehive.services.RoleOps._
import org.thp.thehive.services.UserOps._
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}

import scala.util.{Failure, Success, Try}

@Singleton
class UserSrv @Inject() (
    configuration: Configuration,
    roleSrv: RoleSrv,
    auditSrv: AuditSrv,
    attachmentSrv: AttachmentSrv,
    @Named("integrity-check-actor") integrityCheckActor: ActorRef,
    @Named("with-thehive-schema") implicit val db: Database
) extends VertexSrv[User] {
  val defaultUserDomain: Option[String] = configuration.getOptional[String]("auth.defaultUserDomain")
  val fullUserNameRegex: Pattern        = "[\\p{Graph}&&[^@.]](?:[\\p{Graph}&&[^@]]*)*@\\p{Alnum}+(?:[\\p{Alnum}-.])*".r.pattern

  val userAttachmentSrv = new EdgeSrv[UserAttachment, User, Attachment]

  override def createEntity(e: User)(implicit graph: Graph, authContext: AuthContext): Try[User with Entity] = {
    integrityCheckActor ! IntegrityCheckActor.EntityAdded("User")
    super.createEntity(e)
  }

  def checkUser(user: User): Try[User] = {
    val login =
      if (!user.login.contains('@') && defaultUserDomain.isDefined) s"${user.login}@${defaultUserDomain.get}".toLowerCase
      else user.login.toLowerCase

    if (fullUserNameRegex.matcher(login).matches() && login != "system@thehive.local") Success(user.copy(login = login))
    else Failure(BadRequestError(s"User login is invalid, it must be an email address (found: ${user.login})"))
  }

  // TODO return Try[Unit]
  def addUserToOrganisation(user: User with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichUser] =
    (if (!get(user).organisations.getByName(organisation.name).exists)
       roleSrv.create(user, organisation, profile)
     else
       Success(())).flatMap { _ =>
      for {
        richUser <- get(user).richUser(organisation.name).getOrFail("User")
        _        <- auditSrv.user.create(user, richUser.toJson)
      } yield richUser
    }

  def addOrCreateUser(user: User, avatar: Option[FFile], organisation: Organisation with Entity, profile: Profile with Entity)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichUser] =
    get(user.login)
      .getOrFail("User")
      .orElse {
        for {
          validUser   <- checkUser(user)
          createdUser <- createEntity(validUser)
          _           <- avatar.map(setAvatar(createdUser, _)).flip
        } yield createdUser
      }
      .flatMap(addUserToOrganisation(_, organisation, profile))

  def canSetPassword(user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Boolean = {
    val userOrganisations     = get(user).organisations.value(_.name).toSet
    val operatorOrganisations = current.organisations(Permissions.manageUser).value(_.name).toSeq
    operatorOrganisations.contains(Organisation.administration.name) || (userOrganisations -- operatorOrganisations).isEmpty
  }

  def delete(user: User with Entity, organisation: Organisation with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    if (get(user).organisations.hasNot("name", organisation.name).exists)
      get(user).role.filter(_.organisation.has("name", organisation.name)).remove()
    else {
      get(user).role.remove()
      get(user).remove()
    }
    auditSrv.user.delete(user, organisation)
  }

  override def exists(e: User)(implicit graph: Graph): Boolean = startTraversal.getByName(e.login).exists

  def lock(user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[User with Entity] =
    if (user.login == authContext.userId)
      Failure(AuthorizationError("You cannot lock yourself"))
    else
      for {
        updatedUser <- get(user).update(_.locked, true: Boolean).getOrFail("User")
        _           <- auditSrv.user.update(updatedUser, Json.obj("locked" -> true))
      } yield updatedUser

  def unlock(user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[User with Entity] =
    for {
      updatedUser <- get(user).update(_.locked, false: Boolean).getOrFail("User")
      _           <- auditSrv.user.update(updatedUser, Json.obj("locked" -> false))
    } yield updatedUser

  def current(implicit graph: Graph, authContext: AuthContext): Traversal.V[User] = get(authContext.userId)

  override def get(idOrName: String)(implicit graph: Graph): Traversal.V[User] =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else
      defaultUserDomain.fold(startTraversal.getByName(idOrName)) {
        case d if !idOrName.contains('@') => startTraversal.getByName(s"$idOrName@$d")
        case _                            => startTraversal.getByName(idOrName)
      }

  override def update(
      traversal: Traversal.V[User],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[User], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (userSteps, updatedFields) =>
        userSteps
          .filterNot(_.systemUser)
          .clone()
          .getOrFail("User")
          .flatMap(auditSrv.user.update(_, updatedFields))
    }

  def setAvatar(user: User with Entity, avatar: FFile)(implicit graph: Graph, authContext: AuthContext): Try[String] =
    attachmentSrv.create(avatar).flatMap(setAvatar(user, _))

  def setAvatar(user: User with Entity, avatar: Attachment with Entity)(implicit graph: Graph, authContext: AuthContext): Try[String] = {
    unsetAvatar(user)
    userAttachmentSrv.create(UserAttachment(), user, avatar).map(_ => avatar.attachmentId)
  }

  def unsetAvatar(user: User with Entity)(implicit graph: Graph): Unit = get(user).avatar.remove()

  def setProfile(user: User with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      role <- get(user).role.filter(_.organisation.getByIds(organisation._id)).getOrFail("User")
      _ = roleSrv.updateProfile(role, profile)
      _ <- auditSrv.user.changeProfile(user, organisation, profile)
    } yield ()
}

object UserOps {

  implicit class UserOpsDefs(traversal: Traversal.V[User]) {
    def current(authContext: AuthContext): Traversal.V[User] = getByName(authContext.userId)

    def get(idOrName: String)(implicit db: Database): Traversal.V[User] =
      if (db.isValidId(idOrName)) traversal.getByIds(idOrName)
      else getByName(idOrName)

    def getByName(login: String): Traversal.V[User] = traversal.has("login", login.toLowerCase)

    def visible(implicit authContext: AuthContext): Traversal.V[User] =
      if (authContext.isPermitted(Permissions.manageOrganisation.permission)) traversal
      else
        traversal.filter(_.or(_.organisations.visibleOrganisationsTo.getByName(authContext.organisation), _.systemUser))

    def can(requiredPermission: Permission)(implicit authContext: AuthContext, db: Database): Traversal.V[User] =
      traversal.filter(_.organisations(requiredPermission).getByName(authContext.organisation))

    def getByAPIKey(key: String): Traversal.V[User] = traversal.has("apikey", key).v[User]

    def organisations: Traversal.V[Organisation] = traversal.out[UserRole].out[RoleOrganisation].v[Organisation]

    protected def organisations0(requiredPermission: String): Traversal.V[Organisation] =
      traversal
        .out[UserRole]
        .filter(_.out[RoleProfile].has("permissions", requiredPermission))
        .out[RoleOrganisation]
        .v[Organisation]

    def organisations(requiredPermission: String)(implicit db: Database): Traversal.V[Organisation] = {
      val isInAdminOrganisation = traversal.clone().organisations0(requiredPermission).getByName(Organisation.administration.name).exists
      if (isInAdminOrganisation) db.labelFilter("Organisation")(traversal.V()).v[Organisation]
      else organisations0(requiredPermission)
    }

    def organisationWithRole: Traversal[Seq[(String, String)], JList[JMap[String, Any]], CList[(String, String), JMap[String, Any], Converter[
      (String, String),
      JMap[String, Any]
    ]]] =
      traversal
        .out[UserRole]
        .project(
          _.by(_.out[RoleOrganisation].v[Organisation].value(_.name))
            .by(_.out[RoleProfile].v[Profile].value(_.name))
        )
        .fold

    def config: Traversal.V[Config] = traversal.out[UserConfig].v[Config]

    def getAuthContext(
        requestId: String,
        organisation: Option[String]
    ): Traversal[AuthContext, JMap[String, Any], Converter[AuthContext, JMap[String, Any]]] = {
      val organisationName = organisation
        .orElse(
          traversal
            .clone()
            .out[UserRole]
            .sort(_.by("_createdAt", Order.asc))
            .out[RoleOrganisation]
            .v[Organisation]
            .value(_.name)
            .headOption
        )
        .getOrElse(Organisation.administration.name)
      getAuthContext(requestId, organisationName)
    }

    def getAuthContext(
        requestId: String,
        organisationName: String
    ): Traversal[AuthContext, JMap[String, Any], Converter[AuthContext, JMap[String, Any]]] =
      traversal
        .filter(_.organisations.getByName(organisationName))
        .has("locked", false)
        .project(
          _.byValue(_.login)
            .byValue(_.name)
            .by(_.out[UserRole].filter(_.out[RoleOrganisation].has("name", organisationName)).out[RoleProfile].v[Profile])
        )
        .domainMap {
          case (userId, userName, profile) =>
            val scope =
              if (organisationName == Organisation.administration.name) "admin"
              else "organisation"
            val permissions = Permissions.forScope(scope) & profile.permissions
            AuthContextImpl(userId, userName, organisationName, requestId, permissions)
        }

    def profile(organisation: String): Traversal.V[Profile] =
      traversal.out[UserRole].filter(_.out[RoleOrganisation].has("name", organisation)).out[RoleProfile].v[Profile]

    def richUser(implicit authContext: AuthContext): Traversal[RichUser, JMap[String, Any], Converter[RichUser, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.avatar.fold)
            .by(_.role.project(_.by(_.profile).by(_.organisation.visible.value(_.name).fold)).fold)
        )
        .domainMap {
          case (user, attachment, profileOrganisations) =>
            profileOrganisations
              .find(_._2.contains(authContext.organisation))
              .orElse(profileOrganisations.headOption)
              .fold(throw InternalError(s"")) { // FIXME
                case (profile, organisationName) =>
                  val avatar = attachment.headOption.map(_.attachmentId)
                  RichUser(user, avatar, profile.name, profile.permissions, organisationName.headOption.getOrElse("***"))
              }
        }

    def richUser(organisation: String): Traversal[RichUser, JMap[String, Any], Converter[RichUser, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.profile(organisation).fold)
            .by(_.avatar.fold)
        )
        .domainMap {
          case (user, profiles, attachment) =>
            RichUser(
              user,
              attachment.headOption.map(_.attachmentId),
              profiles.headOption.fold("")(_.name),
              profiles.headOption.fold(Set.empty[Permission])(_.permissions),
              organisation
            )
        }

    def richUserWithCustomRenderer[D, G, C <: Converter[D, G]](
        organisation: String,
        entityRenderer: Traversal.V[User] => Traversal[D, G, C]
    ): Traversal[(RichUser, D), JMap[String, Any], Converter[(RichUser, D), JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.profile(organisation).fold)
            .by(_.avatar.fold)
            .by(entityRenderer)
        )
        .domainMap {
          case (user, profiles, attachment, renderedEntity) =>
            RichUser(
              user,
              attachment.headOption.map(_.attachmentId),
              profiles.headOption.fold("")(_.name),
              profiles.headOption.fold(Set.empty[Permission])(_.permissions),
              organisation
            ) -> renderedEntity
        }

    def config(configName: String): Traversal.V[Config] =
      traversal.out[UserConfig].has("name", configName).v[Config]

    def role: Traversal.V[Role] = traversal.out[UserRole].v[Role]

    def avatar: Traversal.V[Attachment] = traversal.out[UserAttachment].v[Attachment]

    def systemUser: Traversal.V[User] = traversal.has("login", User.system.login)

    def dashboards: Traversal.V[Dashboard] = traversal.in[DashboardUser].v[Dashboard]

    def tasks: Traversal.V[Task] = traversal.in[TaskUser].v[Task]

    def cases: Traversal.V[Case] = traversal.in[CaseUser].v[Case]
  }
}

@Singleton
class UserIntegrityCheckOps @Inject() (
    @Named("with-thehive-schema") val db: Database,
    val service: UserSrv,
    profileSrv: ProfileSrv,
    organisationSrv: OrganisationSrv,
    roleSrv: RoleSrv
) extends IntegrityCheckOps[User] {

  override def initialCheck()(implicit graph: Graph, authContext: AuthContext): Unit = {
    super.initialCheck()
    val adminUserIsCreated = service
      .get(User.init.login)
      .role
      .filter(_.profile.getByName(Profile.admin.name))
      .organisation
      .getByName(Organisation.administration.name)
      .exists
    if (!adminUserIsCreated)
      for {
        adminUser         <- service.getOrFail(User.init.login)
        adminProfile      <- profileSrv.getOrFail(Profile.admin.name)
        adminOrganisation <- organisationSrv.getOrFail(Organisation.administration.name)
        _                 <- roleSrv.create(adminUser, adminOrganisation, adminProfile)
      } yield ()
    ()
  }

  override def check(): Unit = {
    super.check()
    db.tryTransaction { implicit graph =>
      duplicateInEdges[TaskUser](service.startTraversal).flatMap(firstCreatedElement(_)).foreach(e => removeEdges(e._2))
      duplicateInEdges[CaseUser](service.startTraversal).flatMap(firstCreatedElement(_)).foreach(e => removeEdges(e._2))
      duplicateLinks[Vertex, Vertex](
        service.startTraversal,
        (_.out("UserRole"), _.in("UserRole")),
        (_.out("RoleOrganisation"), _.in("RoleOrganisation"))
      ).flatMap(firstCreatedElement(_)).foreach(e => removeVertices(e._2))
      Success(())
    }
    ()
  }

  override def resolve(entities: Seq[User with Entity])(implicit graph: Graph): Try[Unit] = {
    firstCreatedEntity(entities).foreach {
      case (firstUser, otherUsers) =>
        otherUsers.foreach(copyEdge(_, firstUser))
        otherUsers.foreach(service.get(_).remove())
    }
    Success(())
  }
}
