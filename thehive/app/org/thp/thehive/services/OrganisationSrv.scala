package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.thehive.models._
import play.api.libs.json.JsObject

import scala.util.{Success, Try}

object OrganisationSrv {
  val default = Organisation("default", "initial organisation")
}

@Singleton
class OrganisationSrv @Inject()(roleSrv: RoleSrv, profileSrv: ProfileSrv, auditSrv: AuditSrv)(implicit db: Database)
    extends VertexSrv[Organisation, OrganisationSteps] {

  override val initialValues: Seq[Organisation] = Seq(OrganisationSrv.default)
  val organisationOrganisationSrv               = new EdgeSrv[OrganisationOrganisation, Organisation, Organisation]
  val organisationShareSrv                      = new EdgeSrv[OrganisationShare, Organisation, Share]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): OrganisationSteps = new OrganisationSteps(raw)

  def create(organisation: Organisation, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] =
    for {
      createdOrganisation <- create(organisation)
      _                   <- roleSrv.create(user, createdOrganisation, profileSrv.all)
    } yield createdOrganisation

  def create(e: Organisation)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] = createEntity(e)

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

  def link(
      fromOrg: Organisation with Entity,
      toOrg: Organisation with Entity
  )(implicit authContext: AuthContext, graph: Graph): Try[Unit] = {
    val existing = get(fromOrg).link(toOrg._id).getOrFail()

    if (existing.isSuccess) Success(())
    else organisationOrganisationSrv.create(OrganisationOrganisation(), fromOrg, toOrg).map(_ => ())
  }

}

@EntitySteps[Case]
class OrganisationSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Organisation](raw) {

  def link(orgId: String): OrganisationSteps =
    newInstance(
      raw
        .outTo[OrganisationOrganisation]
        .filter(_.hasId(orgId))
    )

  def links: OrganisationSteps = newInstance(raw.outTo[OrganisationOrganisation])

  def cases: CaseSteps = new CaseSteps(raw.outTo[OrganisationShare].outTo[ShareCase])

  def users: UserSteps = new UserSteps(raw.inTo[RoleOrganisation].inTo[UserRole])

  def shares: ShareSteps = new ShareSteps(raw.outTo[OrganisationShare])

  def caseTemplates: CaseTemplateSteps = new CaseTemplateSteps(raw.inTo[CaseTemplateOrganisation])

  def users(requiredPermission: String): UserSteps = new UserSteps(
    raw
      .inTo[RoleOrganisation]
      .filter(_.outTo[RoleProfile].has(Key("permissions") of requiredPermission))
      .inTo[UserRole]
  )

  def alerts: AlertSteps = new AlertSteps(raw.inTo[AlertOrganisation])

  def dashboards: DashboardSteps = new DashboardSteps(raw.outTo[OrganisationDashboard])

  def visible(implicit authContext: AuthContext): OrganisationSteps =
    if (authContext.permissions.contains(Permissions.manageOrganisation)) this
    else
      newInstance(
        raw.or(
          _.inTo[RoleOrganisation].inTo[UserRole].has(Key("login") of authContext.userId),
          _.inTo[OrganisationOrganisation].inTo[RoleOrganisation].inTo[UserRole].has(Key("login") of authContext.userId)
        )
      )

  def visibleOrganisations: OrganisationSteps = new OrganisationSteps(raw.unionFlat(identity, _.outTo[OrganisationOrganisation]).dedup())

  def config: ConfigSteps = new ConfigSteps(raw.outTo[OrganisationConfig])

  def get(idOrName: String): OrganisationSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): OrganisationSteps = newInstance(raw.has(Key("name") of name))

  override def newInstance(newRaw: GremlinScala[Vertex]): OrganisationSteps = new OrganisationSteps(newRaw)

  override def newInstance(): OrganisationSteps = new OrganisationSteps(raw.clone())
}
