package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._

import scala.util.Try

@Singleton
class OrganisationSrv @Inject()(roleSrv: RoleSrv, profileSrv: ProfileSrv)(implicit db: Database) extends VertexSrv[Organisation, OrganisationSteps] {

  val organisationOrganisationSrv = new EdgeSrv[OrganisationOrganisation, Organisation, Organisation]
  val organisationShareSrv        = new EdgeSrv[OrganisationShare, Organisation, Share]

  override val initialValues: Seq[Organisation]                                           = Seq(Organisation("default"))
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): OrganisationSteps = new OrganisationSteps(raw)

  override def get(id: String)(implicit graph: Graph): OrganisationSteps =
    if (db.isValidId(id)) super.get(id)
    else initSteps.getByName(id)

  def create(organisation: Organisation, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Organisation with Entity] =
    for {
      createdOrganisation <- create(organisation)
      _                   <- roleSrv.create(user, createdOrganisation, profileSrv.admin)
    } yield createdOrganisation

  def current(implicit graph: Graph, authContext: AuthContext): OrganisationSteps = get(authContext.organisation)
}

@EntitySteps[Case]
class OrganisationSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph)
    extends BaseVertexSteps[Organisation, OrganisationSteps](raw) {

  override def newInstance(raw: GremlinScala[Vertex]): OrganisationSteps = new OrganisationSteps(raw)

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

  def visibleOrganisations: OrganisationSteps = new OrganisationSteps(raw.unionFlat(identity, _.outTo[OrganisationOrganisation]).dedup())

  override def get(id: String): OrganisationSteps =
    if (db.isValidId(id)) getById(id)
    else getByName(id)

  def getById(id: String): OrganisationSteps = newInstance(raw.hasId(id))

  def getByName(name: String): OrganisationSteps = newInstance(raw.has(Key("name") of name))
}
