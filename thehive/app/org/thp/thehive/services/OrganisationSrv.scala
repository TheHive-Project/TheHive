package org.thp.thehive.services

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.{Converter, Graph, StepLabel, Traversal}
import org.thp.scalligraph.{BadRequestError, EntityId, EntityIdOrName, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import play.api.cache.SyncCacheApi
import play.api.libs.json.JsObject

import java.util.{Map => JMap}
import scala.util.{Failure, Success, Try}

class OrganisationSrv(
    appConfig: ApplicationConfig,
    _taxonomySrv: => TaxonomySrv,
    _roleSrv: => RoleSrv,
    _profileSrv: => ProfileSrv,
    _auditSrv: => AuditSrv,
    _userSrv: => UserSrv,
    integrityCheckActor: => ActorRef @@ IntegrityCheckTag,
    cache: SyncCacheApi
) extends VertexSrv[Organisation]
    with TheHiveOpsNoDeps {
  lazy val taxonomySrv: TaxonomySrv = _taxonomySrv
  lazy val roleSrv: RoleSrv         = _roleSrv
  lazy val profileSrv: ProfileSrv   = _profileSrv
  lazy val auditSrv: AuditSrv       = _auditSrv
  lazy val userSrv: UserSrv         = _userSrv
  val organisationOrganisationSrv   = new EdgeSrv[OrganisationOrganisation, Organisation, Organisation]
  val organisationShareSrv          = new EdgeSrv[OrganisationShare, Organisation, Share]
  val organisationTaxonomySrv       = new EdgeSrv[OrganisationTaxonomy, Organisation, Taxonomy]

  override def createEntity(e: Organisation)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] = {
    integrityCheckActor ! EntityAdded("Organisation")
    super.createEntity(e)
  }

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Organisation] = startTraversal.getByName(name)

  def create(organisation: Organisation, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] =
    for {
      createdOrganisation <- create(organisation)
      _                   <- roleSrv.create(user, createdOrganisation, profileSrv.orgAdmin)
    } yield createdOrganisation

  def create(e: Organisation)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] = {
    val activeTaxos = getByName("admin").taxonomies.toSeq
    for {
      newOrga <- createEntity(e)
      _       <- taxonomySrv.createFreetagTaxonomy(newOrga)
      _       <- activeTaxos.toTry(t => organisationTaxonomySrv.create(OrganisationTaxonomy(), newOrga, t))
      _       <- auditSrv.organisation.create(newOrga, newOrga.toJson)
    } yield newOrga
  }

  def current(implicit graph: Graph, authContext: AuthContext): Traversal.V[Organisation] = get(authContext.organisation)

  def currentId(implicit graph: Graph, authContext: AuthContext): EntityId =
    getId(authContext.organisation)

  def getId(organisationIdOrName: EntityIdOrName)(implicit graph: Graph): EntityId =
    organisationIdOrName.fold(
      identity,
      name =>
        cache.getOrElseUpdate(s"organisation-id-$name") {
          val oid = getByName(name)._id.getOrFail("Organisation").get
          cache.set(s"organisation-name-$oid", name)
          oid
        }
    )

  def getName(organisationIdOrName: EntityIdOrName)(implicit graph: Graph): String =
    organisationIdOrName.fold(
      oid =>
        cache.getOrElseUpdate(s"organisation-name-$oid") {
          val name = getByIds(oid).value(_.name).getOrFail("Organisation").get
          cache.set(s"organisation-id-$name", oid)
          name
        },
      identity
    )

  val sharingProfilesConfig: ConfigItem[Seq[SharingProfile], Seq[SharingProfile]] =
    appConfig.item[Seq[SharingProfile]]("sharingProfiles", "Share profiles")
  def sharingProfiles: Seq[SharingProfile] = sharingProfilesConfig.get
  def getSharingProfile(name: String): SharingProfile =
    sharingProfiles.find(_.name == name).getOrElse(SharingProfile.default)

  def visibleOrganisation(implicit graph: Graph, authContext: AuthContext): Traversal.V[Organisation] =
    current.visibleOrganisations

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

  def link(orgA: Organisation with Entity, orgB: Organisation with Entity, linkTypeAB: String, linkTypeBA: String)(implicit
      authContext: AuthContext,
      graph: Graph
  ): Try[Unit] = {
    def doLink(from: Organisation with Entity, to: Organisation with Entity, linkType: String): Try[Unit] =
      if (get(from).linkTo(to).update(_.linkType, linkType).exists) Success(())
      else organisationOrganisationSrv.create(OrganisationOrganisation(linkType), from, to).map(_ => ())

    if (orgA.name == "admin" || orgB.name == "admin") Failure(BadRequestError("Admin organisation cannot be linked with other organisation"))
    else if (orgA.name == orgB.name) Failure(BadRequestError("An organisation cannot be linked with itself"))
    else
      for {
        _ <- doLink(orgA, orgB, linkTypeAB)
        _ <- doLink(orgB, orgA, linkTypeBA)
      } yield ()
  }

  def unlink(orgA: Organisation with Entity, orgB: Organisation with Entity)(implicit graph: Graph): Try[Unit] =
    Try {
      get(orgA).linkTo(orgB).remove()
      get(orgB).linkTo(orgA).remove()
    }

}

trait OrganisationOpsNoDeps { _: TheHiveOpsNoDeps =>

  implicit class OrganisationOpsNoDepsDefs(traversal: Traversal.V[Organisation]) {

    def get(idOrName: EntityIdOrName): Traversal.V[Organisation] =
      idOrName.fold(traversal.getByIds(_), traversal.getByName(_))

    def current(implicit authContext: AuthContext): Traversal.V[Organisation] = get(authContext.organisation)

    def linkTo(otherOrg: Organisation with Entity): Traversal.E[OrganisationOrganisation] =
      traversal.outE[OrganisationOrganisation].filter(_.inV.getByIds(otherOrg._id))

    def links: Traversal.V[Organisation] = traversal.out[OrganisationOrganisation].v[Organisation]

    def ownerSharingProfile(
        taskRule: Option[String],
        observableRule: Option[String]
    ): Traversal[SharingProfile, Vertex, Converter[SharingProfile, Vertex]] =
      traversal.domainMap { org =>
        SharingProfile(
          "owner",
          "Owner sharing profile",
          autoShare = true,
          editable = true,
          Profile.orgAdmin.name,
          taskRule.getOrElse(org.taskRule),
          observableRule.getOrElse(org.observableRule)
        )
      }

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
        traversal.filter(_.visibleOrganisations.current)

    def richOrganisation(implicit
        authContext: AuthContext
    ): Traversal[RichOrganisation, JMap[String, Any], Converter[RichOrganisation, JMap[String, Any]]] = {
      val fromOrgLabel   = StepLabel.v[Organisation]
      val linkLabel      = StepLabel.e[OrganisationOrganisation]
      val otherLinkLabel = StepLabel.e[OrganisationOrganisation]
      val toOrgLabel     = StepLabel.v[Organisation]
      traversal
        .project(
          _.by
            .by(
              _.as(fromOrgLabel)
                .outE[OrganisationOrganisation]
                .as(linkLabel)
                .inV
                .v[Organisation]
                .visible
                .as(toOrgLabel)
                .outE[OrganisationOrganisation]
                .as(otherLinkLabel)
                .inV
                .where(P.eq(fromOrgLabel.name))
                .select((toOrgLabel, linkLabel, otherLinkLabel))
                .fold
            )
        )
        .domainMap {
          case (organisation, linkedOrganisations) =>
            RichOrganisation(organisation, linkedOrganisations.map(ol => ol._1 -> (ol._2.linkType -> ol._3.linkType)).toMap)
        }
    }

    def notAdmin: Traversal.V[Organisation] = traversal.hasNot(_.name, Organisation.administration.name)

    def isAdmin: Boolean = traversal.has(_.name, Organisation.administration.name).exists

    def users: Traversal.V[User] = traversal.in[RoleOrganisation].in[UserRole].v[User]

    def userProfile(login: String): Traversal.V[Profile] =
      roles.filter(_.user.has(_.login, login)).profile

    def visibleOrganisations: Traversal.V[Organisation] =
      traversal.unionFlat(identity, _.both[OrganisationOrganisation]).dedup().v[Organisation]

    def config: Traversal.V[Config] = traversal.out[OrganisationConfig].v[Config]

    def getByName(name: String): Traversal.V[Organisation] = traversal.has(_.name, name)
  }
}

trait OrganisationOps {
  _: TheHiveOps =>
  protected val organisationSrv: OrganisationSrv
  protected val customFieldSrv: CustomFieldSrv

  implicit class OrganisationOpsDefs(val traversal: Traversal.V[Organisation]) {

    def sharingProfiles(sharingParameters: Map[String, SharingParameter]): Map[EntityId, SharingProfile] = {
      val parametersWithIds: Map[EntityId, SharingParameter] = sharingParameters.map {
        case (org, share) => organisationSrv.getId(EntityIdOrName(org))(traversal.graph) -> share
      }
      val orgaSharingProfiles = traversal
        .outE[OrganisationOrganisation]
        .project(_.by(_.inV._id).byValue(_.linkType))
        .toIterator
        .map {
          case (orgId, shareName) => orgId -> organisationSrv.getSharingProfile(shareName)
        }
        .toMap

      (parametersWithIds.keySet ++ orgaSharingProfiles.keySet).map { orgId =>
        val share = orgaSharingProfiles.getOrElse(orgId, SharingProfile.default)
        orgId -> parametersWithIds.get(orgId).fold(share)(p => share.applyWith(p))
      }.toMap
    }
  }
}

class OrganisationIntegrityCheckOps(val db: Database, val service: OrganisationSrv) extends IntegrityCheckOps[Organisation] {
  override def resolve(entities: Seq[Organisation with Entity])(implicit graph: Graph): Try[Unit] =
    entities match {
      case head :: tail =>
        tail.foreach(copyEdge(_, head))
        service.getByIds(tail.map(_._id): _*).remove()
        Success(())
      case _ => Success(())
    }

  override def globalCheck(): Map[String, Int] = Map.empty
}
