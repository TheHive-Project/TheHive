package org.thp.thehive.services

import java.util.UUID

import scala.util.Try

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._

@Singleton
class OrganisationSrv @Inject()(roleSrv: RoleSrv, profileSrv: ProfileSrv)(implicit db: Database) extends VertexSrv[Organisation, OrganisationSteps] {

  val organisationOrganisationSrv = new EdgeSrv[OrganisationOrganisation, Organisation, Organisation]
  val organisationShareSrv        = new EdgeSrv[OrganisationShare, Organisation, Share]

  override val initialValues: Seq[Organisation]                                           = Seq(Organisation("default"))
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): OrganisationSteps = new OrganisationSteps(raw)

  def create(organisation: Organisation, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Organisation with Entity = {
    val createdOrganisation = create(organisation)
    roleSrv.create(user, createdOrganisation, profileSrv.admin)
    createdOrganisation
  }

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

//  override def filter(f: EntityFilter[Vertex]): OrganisationSteps        = newInstance(f(raw))

  override def get(id: String): OrganisationSteps =
    Try(UUID.fromString(id))
      .map(_ => getById(id))
      .getOrElse(getByName(id))

  def getById(id: String): OrganisationSteps = newInstance(raw.has(Key("_id") of id))

  def getByName(name: String): OrganisationSteps = newInstance(raw.has(Key("name") of name))
}
