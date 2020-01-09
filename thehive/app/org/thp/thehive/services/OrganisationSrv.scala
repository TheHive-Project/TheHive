package org.thp.thehive.services

import gremlin.scala._
import scala.collection.JavaConverters._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import play.api.libs.json.JsObject
import scala.util.{Success, Try}
import org.thp.scalligraph.RichSeq

object OrganisationSrv {
  val administration = Organisation("admin", "organisation for administration")
}

@Singleton
class OrganisationSrv @Inject()(roleSrv: RoleSrv, profileSrv: ProfileSrv, auditSrv: AuditSrv)(implicit db: Database)
    extends VertexSrv[Organisation, OrganisationSteps] {

  override val initialValues: Seq[Organisation] = Seq(OrganisationSrv.administration)
  val organisationOrganisationSrv               = new EdgeSrv[OrganisationOrganisation, Organisation, Organisation]
  val organisationShareSrv                      = new EdgeSrv[OrganisationShare, Organisation, Share]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): OrganisationSteps = new OrganisationSteps(raw)

  def create(organisation: Organisation, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] =
    for {
      createdOrganisation <- create(organisation)
      _                   <- roleSrv.create(user, createdOrganisation, profileSrv.all)
    } yield createdOrganisation

  def create(e: Organisation)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] =
    for {
      createdOrganisation <- createEntity(e)
      _                   <- auditSrv.organisation.create(createdOrganisation, createdOrganisation.toJson)
    } yield createdOrganisation

  def current(implicit graph: Graph, authContext: AuthContext): OrganisationSteps = get(authContext.organisation)

  override def get(idOrName: String)(implicit graph: Graph): OrganisationSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)

  override def update(
      steps: OrganisationSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(OrganisationSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (orgSteps, updatedFields) =>
        orgSteps
          .newInstance()
          .getOrFail()
          .flatMap(auditSrv.organisation.update(_, updatedFields))
    }

  def linkExists(fromOrg: Organisation with Entity, toOrg: Organisation with Entity)(implicit graph: Graph): Boolean =
    fromOrg._id == toOrg._id || get(fromOrg).links.hasId(toOrg._id).exists()

  def link(fromOrg: Organisation with Entity, toOrg: Organisation with Entity)(implicit authContext: AuthContext, graph: Graph): Try[Unit] =
    if (linkExists(fromOrg, toOrg)) Success(())
    else organisationOrganisationSrv.create(OrganisationOrganisation(), fromOrg, toOrg).map(_ => ())

  def unlink(fromOrg: Organisation with Entity, toOrg: Organisation with Entity)(implicit graph: Graph): Try[Unit] =
    Success(
      get(fromOrg)
        .outToE[OrganisationOrganisation]
        .filter(_.otherV().hasId(toOrg._id))
        .remove()
    )

  def updateLink(fromOrg: Organisation with Entity, toOrganisations: Seq[String])(implicit authContext: AuthContext, graph: Graph): Try[Unit] = {
    val (orgToAdd, orgToRemove) = get(fromOrg)
      .links
      .name
      .toIterator
      .foldLeft((toOrganisations.toSet, Set.empty[String])) {
        case ((toAdd, toRemove), o) if toAdd.contains(o) => (toAdd - o, toRemove)
        case ((toAdd, toRemove), o)                      => (toAdd, toRemove + o)
      }
    for {
      _ <- orgToAdd.toTry(getOrFail(_).flatMap(link(fromOrg, _)))
      _ <- orgToRemove.toTry(getOrFail(_).flatMap(unlink(fromOrg, _)))
    } yield ()
  }
}

@EntitySteps[Organisation]
class OrganisationSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Organisation](raw) {

  def links: OrganisationSteps = newInstance(raw.outTo[OrganisationOrganisation])

  override def newInstance(newRaw: GremlinScala[Vertex]): OrganisationSteps = new OrganisationSteps(newRaw)

  def cases: CaseSteps = new CaseSteps(raw.outTo[OrganisationShare].outTo[ShareCase])

  def shares: ShareSteps = new ShareSteps(raw.outTo[OrganisationShare])

  def caseTemplates: CaseTemplateSteps = new CaseTemplateSteps(raw.inTo[CaseTemplateOrganisation])

  def users(requiredPermission: String): UserSteps = new UserSteps(
    raw
      .inTo[RoleOrganisation]
      .filter(_.outTo[RoleProfile].has(Key("permissions") of requiredPermission))
      .inTo[UserRole]
  )

  def pages: PageSteps = new PageSteps(raw.outTo[OrganisationPage])

  def alerts: AlertSteps = new AlertSteps(raw.inTo[AlertOrganisation])

  def dashboards: DashboardSteps = new DashboardSteps(raw.outTo[OrganisationDashboard])

  def visible(implicit authContext: AuthContext): OrganisationSteps =
    if (authContext.permissions.contains(Permissions.manageOrganisation)) this
    else
      this.filter(_.visibleOrganisationsTo.users.has("login", authContext.userId))

  def richOrganisation: Traversal[RichOrganisation, RichOrganisation] =
    this
      .project(
        _.apply(By[Vertex]())
          .and(By(__[Vertex].outTo[OrganisationOrganisation].fold))
      )
      .map {
        case (organisation, linkedOrganisations) =>
          RichOrganisation(organisation.as[Organisation], linkedOrganisations.asScala.map(_.as[Organisation]))
      }

  def users: UserSteps = new UserSteps(raw.inTo[RoleOrganisation].inTo[UserRole])

  def visibleOrganisationsTo: OrganisationSteps = new OrganisationSteps(raw.unionFlat(_.identity(), _.inTo[OrganisationOrganisation]).dedup())

  def visibleOrganisationsFrom: OrganisationSteps = new OrganisationSteps(raw.unionFlat(_.identity(), _.outTo[OrganisationOrganisation]).dedup())

  def config: ConfigSteps = new ConfigSteps(raw.outTo[OrganisationConfig])

  def get(idOrName: String): OrganisationSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): OrganisationSteps = this.has("name", name)

  override def newInstance(): OrganisationSteps = new OrganisationSteps(raw.clone())
}
