package org.thp.thehive.models
import org.thp.scalligraph.models.{DefineIndex, IndexType}
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@VertexEntity
@DefineIndex(IndexType.unique, "name")
case class Organisation(name: String)

@EdgeEntity[Organisation, Share]
case class OrganisationShare()

@EdgeEntity[Organisation, Organisation]
case class OrganisationOrganisation()
