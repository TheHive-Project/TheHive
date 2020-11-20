package org.thp.thehive.models

import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.models.{DefineIndex, IndexType}
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity}

@BuildVertexEntity
case class Role()

@DefineIndex(IndexType.unique, "name")
@BuildVertexEntity
case class Profile(name: String, permissions: Set[Permission]) {
  def isEditable: Boolean = name != Profile.admin.name && name != Profile.orgAdmin.name
}

object Profile {
  val admin: Profile = Profile("admin", Permissions.adminPermissions)

  val analyst: Profile = Profile(
    "analyst",
    Set(
      Permissions.manageCase,
      Permissions.manageObservable,
      Permissions.manageAlert,
      Permissions.manageTask,
      Permissions.manageAction,
      Permissions.manageShare,
      Permissions.manageAnalyse,
      Permissions.managePage,
      Permissions.accessTheHiveFS
    )
  )
  val readonly: Profile           = Profile("read-only", Set.empty)
  val orgAdmin: Profile           = Profile("org-admin", Permissions.forScope("organisation"))
  val initialValues: Seq[Profile] = Seq(admin, orgAdmin, analyst, readonly)
}

@BuildEdgeEntity[Role, Profile]
case class RoleProfile()

@BuildEdgeEntity[Role, Organisation]
case class RoleOrganisation()
