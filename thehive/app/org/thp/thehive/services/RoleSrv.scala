package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.thehive.models._

import scala.util.Try

class RoleSrv extends VertexSrv[Role] with TheHiveOpsNoDeps {

  val roleOrganisationSrv = new EdgeSrv[RoleOrganisation, Role, Organisation]
  val userRoleSrv         = new EdgeSrv[UserRole, User, Role]
  val roleProfileSrv      = new EdgeSrv[RoleProfile, Role, Profile]

  def create(user: User with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(implicit
      graph: Graph,
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

trait RoleOps { _: TheHiveOpsNoDeps =>
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

@Singleton
class RoleIntegrityCheck @Inject() (
    val db: Database,
    val service: RoleSrv,
    profileSrv: ProfileSrv,
    organisationSrv: OrganisationSrv,
    roleSrv: RoleSrv
) extends GlobalCheck[Role]
    with IntegrityCheckOps[Role] {
  override def globalCheck(traversal: Traversal.V[Role])(implicit graph: Graph): Map[String, Long] = {
    val orgOphanCount      = service.startTraversal.filterNot(_.organisation).sideEffect(_.drop()).getCount
    val userOrphanCount    = service.startTraversal.filterNot(_.user).sideEffect(_.drop()).getCount
    val profileOrphanCount = service.startTraversal.filterNot(_.profile).sideEffect(_.drop()).getCount
    Map("orgOrphan" -> orgOphanCount, "userOrphan" -> userOrphanCount, "profileOrphan" -> profileOrphanCount)
  }
}
