package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@VertexEntity
@DefineIndex(IndexType.unique, "name")
case class Organisation(name: String, description: String)

object Organisation {
  val administration: Organisation     = Organisation("admin", "organisation for administration")
  val initialValues: Seq[Organisation] = Seq(administration)
}

@EdgeEntity[Organisation, Share]
case class OrganisationShare()

@EdgeEntity[Organisation, Organisation]
case class OrganisationOrganisation()

case class RichOrganisation(organisation: Organisation with Entity, links: Seq[Organisation with Entity]) {
  def name: String               = organisation.name
  def description: String        = organisation.description
  def _id: String                = organisation._id
  def _createdAt: Date           = organisation._createdAt
  def _createdBy: String         = organisation._createdBy
  def _updatedAt: Option[Date]   = organisation._updatedAt
  def _updatedBy: Option[String] = organisation._updatedBy
}
