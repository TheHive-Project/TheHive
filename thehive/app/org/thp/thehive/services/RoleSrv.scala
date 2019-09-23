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
class RoleSrv @Inject()(implicit val db: Database) extends VertexSrv[Role, RoleSteps] {

  val roleOrganisationSrv = new EdgeSrv[RoleOrganisation, Role, Organisation]
  val userRoleSrv         = new EdgeSrv[UserRole, User, Role]
  val roleProfileSrv      = new EdgeSrv[RoleProfile, Role, Profile]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): RoleSteps = new RoleSteps(raw)

  def create(user: User with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Role with Entity] =
    for {
      createdRole <- createEntity(Role())
      _           <- roleOrganisationSrv.create(RoleOrganisation(), createdRole, organisation)
      _           <- userRoleSrv.create(UserRole(), user, createdRole)
      _           <- roleProfileSrv.create(RoleProfile(), createdRole, profile)
    } yield createdRole
  // TODO add audit
}

@EntitySteps[Role]
class RoleSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Role, RoleSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): RoleSteps = new RoleSteps(raw)

  def organisation: OrganisationSteps = new OrganisationSteps(raw.outTo[RoleOrganisation])

  def roleProfile(roleProfileSrv: EdgeSrv[RoleProfile, Role, Profile]): EdgeSteps[RoleProfile, Role, Profile] = roleProfileSrv.steps(
    raw
      .outE("RoleProfile")
  )
}
