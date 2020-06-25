package org.thp.thehive.services

import java.util.regex.Pattern
import java.util.{List => JList}

import akka.actor.ActorRef
import gremlin.scala._
import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, TraversalLike, VertexSteps}
import org.thp.scalligraph.{BadRequestError, EntitySteps, RichOptionTry}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

@Singleton
class UserSrv @Inject() (
    configuration: Configuration,
    roleSrv: RoleSrv,
    auditSrv: AuditSrv,
    attachmentSrv: AttachmentSrv,
    @Named("integrity-check-actor") integrityCheckActor: ActorRef,
    @Named("with-thehive-schema") implicit val db: Database
) extends VertexSrv[User, UserSteps] {
  val defaultUserDomain: Option[String] = configuration.getOptional[String]("auth.defaultUserDomain")
  val fullUserNameRegex: Pattern        = "[\\p{Graph}&&[^@.]](?:[\\p{Graph}&&[^@]]*)*@\\p{Alnum}+(?:[\\p{Alnum}-.])*".r.pattern

  val userAttachmentSrv                                                           = new EdgeSrv[UserAttachment, User, Attachment]
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): UserSteps = new UserSteps(raw)

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
  def addUserToOrganisation(user: User with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichUser] =
    (if (!get(user).organisations.getByName(organisation.name).exists())
       roleSrv.create(user, organisation, profile)
     else
       Success(())).flatMap { _ =>
      for {
        richUser <- get(user).richUser(organisation.name).getOrFail()
        _        <- auditSrv.user.create(user, richUser.toJson)
      } yield richUser
    }

  def addOrCreateUser(user: User, avatar: Option[FFile], organisation: Organisation with Entity, profile: Profile with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichUser] =
    get(user.login)
      .getOrFail()
      .orElse {
        for {
          validUser   <- checkUser(user)
          createdUser <- createEntity(validUser)
          _           <- avatar.map(setAvatar(createdUser, _)).flip
        } yield createdUser
      }
      .flatMap(addUserToOrganisation(_, organisation, profile))

  def canSetPassword(user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Boolean = {
    val userOrganisations     = get(user).organisations.name.toList.toSet
    val operatorOrganisations = current.organisations(Permissions.manageUser).name.toList
    operatorOrganisations.contains(Organisation.administration.name) || (userOrganisations -- operatorOrganisations).isEmpty
  }

  def delete(user: User with Entity, organisation: Organisation with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    if (get(user).organisations.hasNot("name", organisation.name).exists())
      get(user).role.filter(_.organisation.has("name", organisation.name)).remove()
    else {
      get(user).role.remove()
      get(user).remove()
    }
    auditSrv.user.delete(user, organisation)
  }

  override def exists(e: User)(implicit graph: Graph): Boolean = initSteps.getByName(e.login).exists()

  def lock(user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[User with Entity] =
    for {
      updatedUser <- get(user).updateOne("locked" -> true)
      _           <- auditSrv.user.update(updatedUser, Json.obj("locked" -> true))
    } yield updatedUser

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
class UserSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph) extends VertexSteps[User](raw) {
  def current(authContext: AuthContext): UserSteps = get(authContext.userId)

  def get(idOrName: String): UserSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(login: String): UserSteps = this.has("login", login.toLowerCase)

  def visible(implicit authContext: AuthContext): UserSteps =
    if (authContext.isPermitted(Permissions.manageOrganisation.permission)) this
    else
      this.filter(_.or(_.organisations.visibleOrganisationsTo.get(authContext.organisation), _.systemUser))

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
    val isInAdminOrganisation = newInstance().organisations0(requiredPermission).get(Organisation.administration.name).exists()
    if (isInAdminOrganisation) new OrganisationSteps(db.labelFilter(db.getVertexModel[Organisation])(graph.V))
    else organisations0(requiredPermission)
  }

  def organisationWithRole: Traversal[JList[(String, String)], JList[(String, String)]] =
    this
      .outTo[UserRole]
      .project(
        _.apply(By(__[Vertex].outTo[RoleOrganisation].value[String]("name")))
          .and(By(__[Vertex].outTo[RoleProfile].value[String]("name")))
      )
      .fold

  def config: ConfigSteps = new ConfigSteps(raw.outTo[UserConfig])

  def getAuthContext(requestId: String, organisation: Option[String]): Traversal[AuthContext, AuthContext] = {
    val organisationName = organisation
      .orElse(
        this
          .newInstance()
          .outTo[UserRole]
          .order(List(By(Key[Long]("_createdAt"), Order.asc)))
          .outTo[RoleOrganisation]
          .value[String](Key("name"))
          .headOption()
      )
      .getOrElse(Organisation.administration.name)
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
            val scope =
              if (organisationName == Organisation.administration.name) "admin"
              else "organisation"
            val permissions = Permissions.forScope(scope) & profile.as[Profile].permissions
            AuthContextImpl(userId, userName, organisationName, requestId, permissions)
        }
    )

  def richUser(organisation: String): Traversal[RichUser, RichUser] =
    this
      .project(
        _.apply(By[Vertex]())
          .and(
            By(
              __[Vertex].coalesce(
                _.outTo[UserRole].filter(_.outTo[RoleOrganisation].has(Key("name") of organisation)).outTo[RoleProfile].fold(),
                _.constant(List.empty[Vertex].asJava)
              )
            )
          )
          .and(By(__[Vertex].outTo[UserAttachment].fold()))
      )
      .collect {
        case (user, profiles, attachment) if profiles.size() == 1 =>
          val profile = profiles.get(0).as[Profile]
          val avatar  = atMostOneOf[Vertex](attachment).map(_.as[Attachment].attachmentId)
          RichUser(user.as[User], avatar, profile.name, profile.permissions, organisation)
        case (user, _, attachment) =>
          val avatar = atMostOneOf[Vertex](attachment).map(_.as[Attachment].attachmentId)
          RichUser(user.as[User], avatar, "", Set.empty, organisation)
      }

  def richUserWithCustomRenderer[A](organisation: String, entityRenderer: UserSteps => TraversalLike[_, A])(
      implicit authContext: AuthContext
  ): Traversal[(RichUser, A), (RichUser, A)] =
    this
      .project(
        _.apply(By[Vertex]())
          .and(
            By(
              __[Vertex].coalesce(
                _.outTo[UserRole].filter(_.outTo[RoleOrganisation].has(Key("name") of organisation)).outTo[RoleProfile].fold(),
                _.constant(List.empty[Vertex].asJava)
              )
            )
          )
          .and(By(__[Vertex].outTo[UserAttachment].fold()))
          .and(By(entityRenderer(newInstance(__[Vertex])).raw))
      )
      .collect {
        case (user, profiles, attachment, renderedEntity) if profiles.size() == 1 =>
          val profile = profiles.get(0).as[Profile]
          val avatar  = atMostOneOf[Vertex](attachment).map(_.as[Attachment].attachmentId)
          RichUser(user.as[User], avatar, profile.name, profile.permissions, organisation) -> renderedEntity
        case (user, _, attachment, renderedEntity) =>
          val avatar = atMostOneOf[Vertex](attachment).map(_.as[Attachment].attachmentId)
          RichUser(user.as[User], avatar, "", Set.empty, organisation) -> renderedEntity
      }

  def role: RoleSteps = new RoleSteps(raw.outTo[UserRole])

  def avatar: AttachmentSteps = new AttachmentSteps(raw.outTo[UserAttachment])

  def systemUser: UserSteps = this.has("login", User.system.login)

  def dashboards: DashboardSteps = new DashboardSteps(raw.inTo[DashboardUser])
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
    for {
      adminUser         <- service.getOrFail(User.init.login)
      adminProfile      <- profileSrv.getOrFail(Profile.admin.name)
      adminOrganisation <- organisationSrv.getOrFail(Organisation.administration.name)
      _                 <- roleSrv.create(adminUser, adminOrganisation, adminProfile)
    } yield ()
    ()
  }

  override def check(): Unit = {
    duplicateEntities
      .foreach { entities =>
        db.tryTransaction { implicit graph =>
          resolve(entities)
        }
      }
    db.tryTransaction { implicit graph =>
      duplicateInEdges[TaskUser](service.initSteps.raw).flatMap(firstCreatedElement(_)).foreach(e => removeEdges(e._2))
      duplicateInEdges[CaseUser](service.initSteps.raw).flatMap(firstCreatedElement(_)).foreach(e => removeEdges(e._2))
      duplicateLinks[Vertex, Vertex](
        service.initSteps.raw,
        (_.out("UserRole"), _.in("UserRole")),
        (_.out("RoleOrganisation"), _.in("RoleOrganisation"))
      ).flatMap(firstCreatedElement(_)).foreach(e => removeVertices(e._2))
      Success(())
    }
    ()
  }

  override def resolve(entities: List[User with Entity])(implicit graph: Graph): Try[Unit] = {
    firstCreatedEntity(entities).foreach {
      case (firstUser, otherUsers) =>
        otherUsers.foreach(copyEdge(_, firstUser))
        otherUsers.foreach(service.get(_).remove())
    }
    Success(())
  }

}
