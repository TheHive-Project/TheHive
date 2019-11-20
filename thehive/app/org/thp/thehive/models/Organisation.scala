package org.thp.thehive.models
import java.util.Date

import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@VertexEntity
@DefineIndex(IndexType.unique, "name")
case class Organisation(name: String, description: String)

@EdgeEntity[Organisation, Share]
case class OrganisationShare()

@EdgeEntity[Organisation, Organisation]
case class OrganisationOrganisation()

@EdgeEntity[Organisation, Page]
case class OrganisationPage()

case class RichOrganisation(organisation: Organisation with Entity, links: Seq[Organisation with Entity]) {
  def name: String               = organisation.name
  def description: String        = organisation.description
  def _id: String                = organisation._id
  val _createdAt: Date           = organisation._createdAt
  val _createdBy: String         = organisation._createdBy
  val _updatedAt: Option[Date]   = organisation._updatedAt
  val _updatedBy: Option[String] = organisation._updatedBy
}
