package org.thp.thehive.models
import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.models.{DefineIndex, IndexType}
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@VertexEntity
case class Role()

@DefineIndex(IndexType.unique, "name")
@VertexEntity
case class Profile(name: String, permissions: Set[Permission])

@EdgeEntity[Role, Profile]
case class RoleProfile()

@EdgeEntity[Role, Organisation]
case class RoleOrganisation()
