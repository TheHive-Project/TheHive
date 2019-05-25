package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._

@Singleton
class RoleSrv @Inject()(implicit val db: Database) extends VertexSrv[Role, RoleSteps] {

  val roleOrganisationSrv = new EdgeSrv[RoleOrganisation, Role, Organisation]
  val userRoleSrv         = new EdgeSrv[UserRole, User, Role]
  val roleProfileSrv      = new EdgeSrv[RoleProfile, Role, Profile]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): RoleSteps = new RoleSteps(raw)

  def create(user: User with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Role with Entity = {
    val createdRole = create(Role())
    roleOrganisationSrv.create(RoleOrganisation(), createdRole, organisation)
    userRoleSrv.create(UserRole(), user, createdRole)
    roleProfileSrv.create(RoleProfile(), createdRole, profile)
    createdRole
  }
}

@EntitySteps[Role]
class RoleSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Role, RoleSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): RoleSteps = new RoleSteps(raw)

//  def richRole: GremlinScala[RichRole] =
//    raw
//      .project(
//        _.apply(By(__[Vertex].outTo[RoleCase]))
//          .and(By(__[Vertex].inTo[OrganisationRole]))
//          .and(By(__[Vertex].outTo[RoleProfile])))
//      .map {
//        case (`case`, organisation, profile) ⇒
//          RichRole(`case`.as[Case], organisation, profile)
////            onlyOneOf[String](m.get[JList[String]]("case")),
////            onlyOneOf[String](m.get[JList[String]]("organisation"))
////          )
//        //def apply(`case`: Case with Entity, organisation: Organisation with Entity, profile: Profile with Entity): RichRole =
//      }

  def organisation: OrganisationSteps = new OrganisationSteps(raw.outTo[RoleOrganisation])

//  def `case`: CaseSteps = new CaseSteps(raw.outTo[RoleCase])
}
