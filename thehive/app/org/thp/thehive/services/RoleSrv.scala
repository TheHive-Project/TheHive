package org.thp.thehive.services

import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.Traversal
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.models._
import org.thp.thehive.services.RoleOps._

import scala.util.Try

@Singleton
class RoleSrv @Inject() (@Named("with-thehive-schema") implicit val db: Database) extends VertexSrv[Role] {

  val roleOrganisationSrv = new EdgeSrv[RoleOrganisation, Role, Organisation]
  val userRoleSrv         = new EdgeSrv[UserRole, User, Role]
  val roleProfileSrv      = new EdgeSrv[RoleProfile, Role, Profile]

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

  def updateProfile(
      role: Role with Entity,
      profile: Profile with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[RoleProfile with Entity] = {
    get(role).removeProfile()
    roleProfileSrv.create(RoleProfile(), role, profile)
  }
}

object RoleOps {
  implicit class RoleOpsDefs(traversal: Traversal.V[Role]) {
    def organisation: Traversal.V[Organisation] = traversal.out[RoleOrganisation].v[Organisation]

    def removeProfile(): Unit = {
      traversal.outE[RoleProfile].remove()
      ()
    }

    def profile: Traversal.V[Profile] = traversal.out[RoleProfile].v[Profile]
    def user: Traversal.V[User]       = traversal.in[UserRole].v[User]

  }
}
