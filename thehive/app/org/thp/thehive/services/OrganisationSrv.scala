package org.thp.thehive.services

import java.util.{Map => JMap}

import akka.actor.ActorRef
import javax.inject.{Inject, Named, Provider, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.scalligraph.{BadRequestError, EntityId, EntityIdOrName, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.RoleOps._
import org.thp.thehive.services.UserOps._
import play.api.libs.json.JsObject

import scala.util.{Failure, Success, Try}

@Singleton
class OrganisationSrv @Inject() (
    taxonomySrvProvider: Provider[TaxonomySrv],
    roleSrv: RoleSrv,
    profileSrv: ProfileSrv,
    auditSrv: AuditSrv,
    userSrv: UserSrv,
    @Named("integrity-check-actor") integrityCheckActor: ActorRef
)(implicit
    @Named("with-thehive-schema") db: Database
) extends VertexSrv[Organisation] {
  lazy val taxonomySrv: TaxonomySrv = taxonomySrvProvider.get
  val organisationOrganisationSrv   = new EdgeSrv[OrganisationOrganisation, Organisation, Organisation]
  val organisationShareSrv          = new EdgeSrv[OrganisationShare, Organisation, Share]

  override def createEntity(e: Organisation)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] = {
    integrityCheckActor ! IntegrityCheckActor.EntityAdded("Organisation")
    super.createEntity(e)
  }

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Organisation] = startTraversal.getByName(name)

  def create(organisation: Organisation, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] =
    for {
      createdOrganisation <- create(organisation)
      _                   <- roleSrv.create(user, createdOrganisation, profileSrv.orgAdmin)
    } yield createdOrganisation

  def create(e: Organisation)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] =
    for {
      createdOrganisation <- createEntity(e)
      _                   <- taxonomySrv.createWithOrg(Taxonomy("custom", "Custom taxonomy", 1), Seq(), createdOrganisation)
      _                   <- auditSrv.organisation.create(createdOrganisation, createdOrganisation.toJson)
    } yield createdOrganisation

  def current(implicit graph: Graph, authContext: AuthContext): Traversal.V[Organisation] = get(authContext.organisation)

  def visibleOrganisation(implicit graph: Graph, authContext: AuthContext): Traversal.V[Organisation] =
    userSrv.current.organisations.visibleOrganisationsFrom

  override def exists(e: Organisation)(implicit graph: Graph): Boolean = startTraversal.getByName(e.name).exists

  override def update(
      traversal: Traversal.V[Organisation],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Organisation], JsObject)] =
    if (traversal.clone().getByName(Organisation.administration.name).exists)
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
    fromOrg._id == toOrg._id || get(fromOrg).links.getEntity(toOrg).exists

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

  def updateLink(fromOrg: Organisation with Entity, toOrganisations: Seq[EntityIdOrName])(implicit
      authContext: AuthContext,
      graph: Graph
  ): Try[Unit] = {
    val (orgToAdd, orgToRemove) = get(fromOrg)
      .links
      ._id
      .toIterator
      .foldLeft((toOrganisations.toSet, Set.empty[EntityId])) {
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

    def get(idOrName: EntityIdOrName): Traversal.V[Organisation] =
      idOrName.fold(traversal.getByIds(_), traversal.getByName(_))

    def current(implicit authContext: AuthContext): Traversal.V[Organisation] = get(authContext.organisation)

    def links: Traversal.V[Organisation] = traversal.out[OrganisationOrganisation].v[Organisation]

    def cases: Traversal.V[Case] = traversal.out[OrganisationShare].out[ShareCase].v[Case]

    def shares: Traversal.V[Share] = traversal.out[OrganisationShare].v[Share]

    def taxonomies: Traversal.V[Taxonomy] = traversal.out[OrganisationTaxonomy].v[Taxonomy]

    def caseTemplates: Traversal.V[CaseTemplate] = traversal.in[CaseTemplateOrganisation].v[CaseTemplate]

    def users(requiredPermission: Permission): Traversal.V[User] =
      traversal.roles.filter(_.profile.has(_.permissions, requiredPermission)).user

    def userPermissions(userId: EntityIdOrName): Traversal[Permission, String, Converter[Permission, String]] =
      traversal
        .roles
        .filter(_.user.get(userId))
        .profile
        .property("permissions", Permission(_: String))

    def roles: Traversal.V[Role] = traversal.in[RoleOrganisation].v[Role]

    def pages: Traversal.V[Page] = traversal.out[OrganisationPage].v[Page]

    def alerts: Traversal.V[Alert] = traversal.in[AlertOrganisation].v[Alert]

    def dashboards: Traversal.V[Dashboard] = traversal.out[OrganisationDashboard].v[Dashboard]

    def visible(implicit authContext: AuthContext): Traversal.V[Organisation] =
      if (authContext.isPermitted(Permissions.manageOrganisation))
        traversal
      else
        traversal.filter(_.visibleOrganisationsTo.users.current)

    def richOrganisation: Traversal[RichOrganisation, JMap[String, Any], Converter[RichOrganisation, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.out[OrganisationOrganisation].v[Organisation].fold)
        )
        .domainMap {
          case (organisation, linkedOrganisations) =>
            RichOrganisation(organisation, linkedOrganisations)
        }

    def isAdmin: Boolean = traversal.has(_.name, Organisation.administration.name).exists

    def users: Traversal.V[User] = traversal.in[RoleOrganisation].in[UserRole].v[User]

    def userProfile(login: String): Traversal.V[Profile] =
      roles.filter(_.user.has(_.login, login)).profile

    def visibleOrganisationsTo: Traversal.V[Organisation] =
      traversal.unionFlat(identity, _.in[OrganisationOrganisation]).dedup().v[Organisation]

    def visibleOrganisationsFrom: Traversal.V[Organisation] =
      traversal.unionFlat(identity, _.out[OrganisationOrganisation]).dedup().v[Organisation]

    def config: Traversal.V[Config] = traversal.out[OrganisationConfig].v[Config]

    def getByName(name: String): Traversal.V[Organisation] = traversal.has(_.name, name)
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
