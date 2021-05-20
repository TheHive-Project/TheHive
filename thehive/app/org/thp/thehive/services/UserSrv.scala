package org.thp.thehive.services

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.Converter.CList
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.scalligraph.{AuthorizationError, BadRequestError, EntityIdOrName, EntityName, RichOptionTry}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}

import java.util.regex.Pattern
import java.util.{List => JList, Map => JMap}
import scala.util.{Failure, Success, Try}

class UserSrv(
    configuration: Configuration,
    roleSrv: RoleSrv,
    auditSrv: AuditSrv,
    attachmentSrv: AttachmentSrv,
    integrityCheckActor: => ActorRef @@ IntegrityCheckTag
) extends VertexSrv[User]
    with TheHiveOpsNoDeps {
  val defaultUserDomain: Option[String] = configuration.getOptional[String]("auth.defaultUserDomain")
  val fullUserNameRegex: Pattern        = "[\\p{Graph}&&[^@.]](?:[\\p{Graph}&&[^@]]*)*@\\p{Alnum}+(?:[\\p{Alnum}-.])*".r.pattern

  val userAttachmentSrv = new EdgeSrv[UserAttachment, User, Attachment]

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
      integrityCheckActor ! EntityAdded("User")
      for {
        richUser <- get(user).richUser(authContext, organisation._id).getOrFail("User")
        _        <- auditSrv.user.create(user, richUser.toJson)
      } yield richUser
    }

  def addOrCreateUser(user: User, avatar: Option[FFile], organisation: Organisation with Entity, profile: Profile with Entity)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichUser] =
    getByName(user.login)
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
    if (get(user).organisations.filterNot(_.get(organisation._id)).exists)
      get(user).role.filter(_.organisation.get(organisation._id)).remove()
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

  def current(implicit graph: Graph, authContext: AuthContext): Traversal.V[User] = get(EntityName(authContext.userId))

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[User] =
    defaultUserDomain.fold(startTraversal.getByName(name)) {
      case d if !name.contains('@') => startTraversal.getByName(s"$name@$d")
      case _                        => startTraversal.getByName(name)
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
      role <- get(user).role.filter(_.organisation.getEntity(organisation)).getOrFail("User")
      _ = roleSrv.updateProfile(role, profile)
      _ <- auditSrv.user.changeProfile(user, organisation, profile)
    } yield ()
}

trait UserOps { _: TheHiveOpsNoDeps =>

  implicit class UserOpsDefs(traversal: Traversal.V[User]) {
    def get(idOrName: EntityIdOrName): Traversal.V[User] =
      idOrName.fold(traversal.getByIds(_), getByName)

    def current(implicit authContext: AuthContext): Traversal.V[User] = getByName(authContext.userId)

    def getByName(login: String): Traversal.V[User] = traversal.has(_.login, login.toLowerCase)

    def visible(implicit authContext: AuthContext): Traversal.V[User] =
      if (authContext.isPermitted(Permissions.manageOrganisation.permission)) traversal
      else
        traversal.filter(_.or(_.organisations.visibleOrganisationsTo.get(authContext.organisation), _.systemUser))

    def isNotLocked: Traversal.V[User] = traversal.has(_.locked, false)

    def can(requiredPermission: Permission)(implicit authContext: AuthContext): Traversal.V[User] =
      traversal.filter(_.organisations(requiredPermission).get(authContext.organisation))

    def getByAPIKey(key: String): Traversal.V[User] = traversal.has(_.apikey, key).v[User]

    def organisations: Traversal.V[Organisation] = traversal.out[UserRole].out[RoleOrganisation].v[Organisation]

    protected def organisations0(requiredPermission: Permission): Traversal.V[Organisation] =
      role.filter(_.profile.has(_.permissions, requiredPermission)).organisation

    def organisations(requiredPermission: Permission): Traversal.V[Organisation] = {
      val isInAdminOrganisation = traversal.clone().organisations0(requiredPermission).getByName(Organisation.administration.name).exists
      if (isInAdminOrganisation) traversal.graph.V[Organisation]()
      else organisations0(requiredPermission)
    }

    def organisationWithRole: Traversal[Seq[(Organisation with Entity, String)], JList[JMap[String, Any]], CList[
      (Organisation with Entity, String),
      JMap[String, Any],
      Converter[(Organisation with Entity, String), JMap[String, Any]]
    ]] =
      role
        .project(
          _.by(_.organisation)
            .by(_.profile.value(_.name))
        )
        .fold

    def config: Traversal.V[Config] = traversal.out[UserConfig].v[Config]

    def getAuthContext(
        requestId: String,
        organisation: Option[EntityIdOrName]
    ): Traversal[AuthContext, JMap[String, Any], Converter[AuthContext, JMap[String, Any]]] = {
      val organisationName = organisation
        .orElse(
          traversal
            .clone()
            .role
            .sort(_.by("_createdAt", Order.asc))
            .organisation
            ._id
            .headOption
        )
        .getOrElse(EntityName(Organisation.administration.name))
      getAuthContext(requestId, organisationName)
    }

    def getAuthContext(
        requestId: String,
        organisationName: EntityIdOrName
    ): Traversal[AuthContext, JMap[String, Any], Converter[AuthContext, JMap[String, Any]]] =
      traversal
        .isNotLocked
        .project(
          _.byValue(_.login)
            .byValue(_.name)
            .by(_.profile(organisationName).fold)
            .by(_.organisations.get(organisationName).value(_.name).limit(1).fold)
            .by(_.profile(EntityName(Organisation.administration.name)).fold)
        )
        .domainMap {
          case (userId, userName, profile, org, adminProfile) =>
            val scope =
              if (org.contains(Organisation.administration.name)) "admin"
              else "organisation"
            val permissions =
              Permissions.forScope(scope) & profile.headOption.orElse(adminProfile.headOption).fold(Set.empty[Permission])(_.permissions)
            AuthContextImpl(userId, userName, organisationName, requestId, permissions)
        }

    def profile(organisation: EntityIdOrName): Traversal.V[Profile] =
      role.filter(_.organisation.get(organisation)).profile

    def richUser(implicit authContext: AuthContext): Traversal[RichUser, JMap[String, Any], Converter[RichUser, JMap[String, Any]]] =
      richUser(authContext, authContext.organisation)

    def richUser(
        authContext: AuthContext,
        organisation: EntityIdOrName
    ): Traversal[RichUser, JMap[String, Any], Converter[RichUser, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.avatar.value(_.attachmentId).option)
            .by(_.role.project(_.by(_.profile).by(_.organisation.visible(authContext).project(_.by(_._id).byValue(_.name)).fold)).fold)
        )
        .domainMap {
          case (user, avatar, profileOrganisations) =>
            organisation
              .fold(id => profileOrganisations.find(_._2.exists(_._1 == id)), name => profileOrganisations.find(_._2.exists(_._2 == name)))
              .orElse(profileOrganisations.headOption)
              .fold(RichUser(user, avatar, Profile.admin.name, Set.empty, "no org")) { // fake user (probably "system")
                case (profile, organisationIdAndName) =>
                  RichUser(user, avatar, profile.name, profile.permissions, organisationIdAndName.headOption.fold("no org")(_._2))
              }
        }

    def richUserWithCustomRenderer[D, G, C <: Converter[D, G]](
        organisation: EntityIdOrName,
        entityRenderer: Traversal.V[User] => Traversal[D, G, C]
    )(implicit authContext: AuthContext): Traversal[(RichUser, D), JMap[String, Any], Converter[(RichUser, D), JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.avatar.value(_.attachmentId).option)
            .by(_.role.project(_.by(_.profile).by(_.organisation.visible.project(_.by(_._id).byValue(_.name)).fold)).fold)
            .by(entityRenderer)
        )
        .domainMap {
          case (user, avatar, profileOrganisations, renderedEntity) =>
            organisation
              .fold(id => profileOrganisations.find(_._2.exists(_._1 == id)), name => profileOrganisations.find(_._2.exists(_._2 == name)))
              .orElse(profileOrganisations.headOption)
              .fold(RichUser(user, avatar, Profile.admin.name, Set.empty, "no org") -> renderedEntity) { // fake user (probably "system")
                case (profile, organisationIdAndName) =>
                  RichUser(user, avatar, profile.name, profile.permissions, organisationIdAndName.headOption.fold("***")(_._2)) -> renderedEntity
              }
        }

    def config(configName: String): Traversal.V[Config] =
      traversal.out[UserConfig].v[Config].has(_.name, configName)

    def role: Traversal.V[Role] = traversal.out[UserRole].v[Role]

    def avatar: Traversal.V[Attachment] = traversal.out[UserAttachment].v[Attachment]

    def systemUser: Traversal.V[User] = traversal.has(_.login, User.system.login)

    def dashboards: Traversal.V[Dashboard] = traversal.in[DashboardUser].v[Dashboard]

    def tasks: Traversal.V[Task] = traversal.in[TaskUser].v[Task]

    def cases: Traversal.V[Case] = traversal.in[CaseUser].v[Case]
  }
}

class UserIntegrityCheckOps(
    val db: Database,
    val service: UserSrv,
    profileSrv: ProfileSrv,
    organisationSrv: OrganisationSrv,
    roleSrv: RoleSrv
) extends IntegrityCheckOps[User]
    with TheHiveOpsNoDeps {

  override def initialCheck()(implicit graph: Graph, authContext: AuthContext): Unit = {
    super.initialCheck()
    val adminUserIsCreated = service
      .getByName(User.init.login)
      .role
      .filter(_.profile.getByName(Profile.admin.name))
      .organisation
      .getByName(Organisation.administration.name)
      .exists
    if (!adminUserIsCreated)
      for {
        adminUser         <- service.getByName(User.init.login).getOrFail("User")
        adminProfile      <- profileSrv.getByName(Profile.admin.name).getOrFail("Profile")
        adminOrganisation <- organisationSrv.getByName(Organisation.administration.name).getOrFail("Organisation")
        _                 <- roleSrv.create(adminUser, adminOrganisation, adminProfile)
      } yield ()
    ()
  }

  override def duplicationCheck(): Map[String, Long] = {
    super.duplicationCheck()
    db.tryTransaction { implicit graph =>
      val duplicateTaskAssignments =
        duplicateInEdges[TaskUser](service.startTraversal).flatMap(firstCreatedElement(_)).map(e => removeEdges(e._2)).size.toLong
      val duplicateCaseAssignments =
        duplicateInEdges[CaseUser](service.startTraversal).flatMap(firstCreatedElement(_)).map(e => removeEdges(e._2)).size.toLong
      val duplicateUsers = duplicateLinks[Vertex, Vertex](
        service.startTraversal,
        (_.out("UserRole"), _.in("UserRole")),
        (_.out("RoleOrganisation"), _.in("RoleOrganisation"))
      ).flatMap(firstCreatedElement(_)).map(e => removeVertices(e._2)).size.toLong
      Success(
        Map(
          "duplicateTaskAssignments" -> duplicateTaskAssignments,
          "duplicateCaseAssignments" -> duplicateCaseAssignments,
          "duplicateUsers"           -> duplicateUsers
        )
      )
    }.getOrElse(Map("globalFailure" -> 1L))
  }

  override def resolve(entities: Seq[User with Entity])(implicit graph: Graph): Try[Unit] = {
    firstCreatedEntity(entities).foreach {
      case (firstUser, otherUsers) =>
        otherUsers.foreach(copyEdge(_, firstUser))
        otherUsers.foreach(service.get(_).remove())
    }
    Success(())
  }

  override def globalCheck(): Map[String, Long] = Map.empty
}
