package org.thp.thehive.services

import akka.actor.ActorRef
import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.scalligraph.{BadRequestError, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services.RoleOps._
import play.api.libs.json.JsObject

import scala.util.{Failure, Success, Try}

@Singleton
class OrganisationSrv @Inject() (
    roleSrv: RoleSrv,
    profileSrv: ProfileSrv,
    auditSrv: AuditSrv,
    userSrv: UserSrv,
    @Named("integrity-check-actor") integrityCheckActor: ActorRef
)(implicit
    @Named("with-thehive-schema") db: Database
) extends VertexSrv[Organisation] {

  val organisationOrganisationSrv = new EdgeSrv[OrganisationOrganisation, Organisation, Organisation]
  val organisationShareSrv        = new EdgeSrv[OrganisationShare, Organisation, Share]

  override def createEntity(e: Organisation)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] = {
    integrityCheckActor ! IntegrityCheckActor.EntityAdded("Organisation")
    super.createEntity(e)
  }

  def create(organisation: Organisation, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] =
    for {
      createdOrganisation <- create(organisation)
      _                   <- roleSrv.create(user, createdOrganisation, profileSrv.orgAdmin)
    } yield createdOrganisation

  def create(e: Organisation)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] =
    for {
      createdOrganisation <- createEntity(e)
      _                   <- auditSrv.organisation.create(createdOrganisation, createdOrganisation.toJson)
    } yield createdOrganisation

  def current(implicit graph: Graph, authContext: AuthContext): Traversal.V[Organisation] = get(authContext.organisation)

  def visibleOrganisation(implicit graph: Graph, authContext: AuthContext): Traversal.V[Organisation] =
    userSrv.current.organisations.visibleOrganisationsFrom

  override def get(idOrName: String)(implicit graph: Graph): Traversal.V[Organisation] =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else startTraversal.getByName(idOrName)

  override def exists(e: Organisation)(implicit graph: Graph): Boolean = startTraversal.getByName(e.name).exists

  override def update(
      traversal: Traversal.V[Organisation],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Organisation], JsObject)] =
    if (traversal.clone().has("name", Organisation.administration.name).exists)
      Failure(BadRequestError("Admin organisation is unmodifiable"))
    else
      auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
        case (orgSteps, updatedFields) =>
          orgSteps
            .clone()
            .getOrFail("Organisation")
            .flatMap(auditSrv.organisation.update(_, updatedFields))
      }

  def linkExists(fromOrg: Organisation with Entity, toOrg: Organisation with Entity)(implicit graph: Graph): Boolean =
    fromOrg._id == toOrg._id || get(fromOrg).links.hasId(toOrg._id).exists

  def link(fromOrg: Organisation with Entity, toOrg: Organisation with Entity)(implicit authContext: AuthContext, graph: Graph): Try[Unit] =
    if (linkExists(fromOrg, toOrg)) Success(())
    else organisationOrganisationSrv.create(OrganisationOrganisation(), fromOrg, toOrg).map(_ => ())

  def doubleLink(org1: Organisation with Entity, org2: Organisation with Entity)(implicit authContext: AuthContext, graph: Graph): Try[Unit] =
    if (org1.name == "admin" || org2.name == "admin") Failure(BadRequestError("Admin organisation cannot be link with other organisation"))
    else
      for {
        _ <- link(org1, org2)
        _ <- link(org2, org1)
      } yield ()

  def unlink(fromOrg: Organisation with Entity, toOrg: Organisation with Entity)(implicit graph: Graph): Try[Unit] =
    Success(
      get(fromOrg)
        .outE[OrganisationOrganisation]
        .filter(_.otherV.hasId(toOrg._id))
        .remove()
    )

  def doubleUnlink(org1: Organisation with Entity, org2: Organisation with Entity)(implicit graph: Graph): Try[Unit] = {
    unlink(org1, org2) // can't fail
    unlink(org2, org1)
  }

  def updateLink(fromOrg: Organisation with Entity, toOrganisations: Seq[String])(implicit authContext: AuthContext, graph: Graph): Try[Unit] = {
    val (orgToAdd, orgToRemove) = get(fromOrg)
      .links
      .property[String, String]("name", identity[String])
      .toIterator
      .foldLeft((toOrganisations.toSet, Set.empty[String])) {
        case ((toAdd, toRemove), o) if toAdd.contains(o) => (toAdd - o, toRemove)
        case ((toAdd, toRemove), o)                      => (toAdd, toRemove + o)
      }
    for {
      _ <- orgToAdd.toTry(getOrFail(_).flatMap(doubleLink(fromOrg, _)))
      _ <- orgToRemove.toTry(getOrFail(_).flatMap(doubleUnlink(fromOrg, _)))
    } yield ()
  }
}

object OrganisationOps {

  implicit class OrganisationOpsDefs(traversal: Traversal.V[Organisation]) {

    def links: Traversal.V[Organisation] = traversal.out[OrganisationOrganisation].v[Organisation]

    def cases: Traversal.V[Case] = traversal.out[OrganisationShare].out[ShareCase].v[Case]

    def shares: Traversal.V[Share] = traversal.out[OrganisationShare].v[Share]

    def caseTemplates: Traversal.V[CaseTemplate] = traversal.in[CaseTemplateOrganisation].v[CaseTemplate]

    def users(requiredPermission: Permission): Traversal.V[User] =
      traversal
        .in[RoleOrganisation]
        .filter(_.out[RoleProfile].has("permissions", requiredPermission))
        .in[UserRole]
        .v[User]

    def userPermissions(userId: String): Traversal[Permission, String, Converter[Permission, String]] =
      traversal
        .roles
        .filter(_.user.has("login", userId))
        .profile
        .property("permissions", Permission(_: String))

    def roles: Traversal.V[Role] = traversal.in[RoleOrganisation].v[Role]

    def pages: Traversal.V[Page] = traversal.out[OrganisationPage].v[Page]

    def alerts: Traversal.V[Alert] = traversal.in[AlertOrganisation].v[Alert]

    def dashboards: Traversal.V[Dashboard] = traversal.out[OrganisationDashboard].v[Dashboard]

    def visible(implicit authContext: AuthContext): Traversal.V[Organisation] =
      if (authContext.isPermitted(Permissions.manageOrganisation)) traversal
      else
        traversal.filter(_.visibleOrganisationsTo.users.has("login", authContext.userId))

    def richOrganisation =
      traversal
        .project(
          _.by
            .by(_.out[OrganisationOrganisation].v[Organisation].fold)
        )
        .domainMap {
          case (organisation, linkedOrganisations) =>
            RichOrganisation(organisation, linkedOrganisations)
        }

    def users: Traversal.V[User] = traversal.in[RoleOrganisation].in[UserRole].v[User]

    def userProfile(login: String): Traversal.V[Profile] =
      traversal
        .in[RoleOrganisation]
        .filter(_.in[UserRole].has("login", login))
        .out[RoleProfile]
        .v[Profile]

    def visibleOrganisationsTo: Traversal.V[Organisation] =
      traversal.unionFlat(identity, _.in[OrganisationOrganisation]).dedup().v[Organisation]

    def visibleOrganisationsFrom: Traversal.V[Organisation] =
      traversal.unionFlat(identity, _.out[OrganisationOrganisation]).dedup().v[Organisation]

    def config: Traversal.V[Config] = traversal.out[OrganisationConfig].v[Config]

    def get(idOrName: String)(implicit db: Database): Traversal.V[Organisation] =
      if (db.isValidId(idOrName)) traversal.getByIds(idOrName)
      else getByName(idOrName)

    def getByName(name: String): Traversal.V[Organisation] = traversal.has("name", name)
  }

}

class OrganisationIntegrityCheckOps @Inject() (@Named("with-thehive-schema") val db: Database, val service: OrganisationSrv)
    extends IntegrityCheckOps[Organisation] {
  override def resolve(entities: Seq[Organisation with Entity])(implicit graph: Graph): Try[Unit] =
    entities match {
      case head :: tail =>
        tail.foreach(copyEdge(_, head))
        service.getByIds(tail.map(_._id): _*).remove()
        Success(())
      case _ => Success(())
    }
}
