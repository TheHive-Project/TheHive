package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.thehive.models._
import play.api.libs.json.JsObject

import scala.util.Try

@Singleton
class OrganisationSrv @Inject()(roleSrv: RoleSrv, profileSrv: ProfileSrv, auditSrv: AuditSrv)(implicit db: Database)
    extends VertexSrv[Organisation, OrganisationSteps] {

  override val initialValues: Seq[Organisation] = Seq(Organisation("default", "initial organisation"))
  val organisationOrganisationSrv               = new EdgeSrv[OrganisationOrganisation, Organisation, Organisation]
  val organisationShareSrv                      = new EdgeSrv[OrganisationShare, Organisation, Share]

  def create(e: Organisation)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] = createEntity(e)

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): OrganisationSteps = new OrganisationSteps(raw)

  def create(organisation: Organisation, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] =
    for {
      createdOrganisation <- create(organisation)
      _                   <- roleSrv.create(user, createdOrganisation, profileSrv.admin)
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
          .clone()
          .getOrFail()
          .flatMap(auditSrv.organisation.update(_, updatedFields))
    }
}

@EntitySteps[Case]
class OrganisationSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph)
    extends BaseVertexSteps[Organisation, OrganisationSteps](raw) {

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
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): OrganisationSteps = newInstance(raw.has(Key("name") of name))

  override def newInstance(raw: GremlinScala[Vertex]): OrganisationSteps = new OrganisationSteps(raw)
}
