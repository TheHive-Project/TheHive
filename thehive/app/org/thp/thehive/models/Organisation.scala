package org.thp.thehive.models

import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity, EntityId}
import org.thp.thehive.services.SharingRule

import java.util.Date

@BuildVertexEntity
@DefineIndex(IndexType.unique, "name")
case class Organisation(name: String, description: String, taskRule: String, observableRule: String)

object Organisation {
  val administration: Organisation     = Organisation("admin", "organisation for administration", SharingRule.default, SharingRule.default)
  val initialValues: Seq[Organisation] = Seq(administration)
}

@BuildEdgeEntity[Organisation, Share]
case class OrganisationShare()

@BuildEdgeEntity[Organisation, Organisation]
case class OrganisationOrganisation(linkType: String)

@BuildEdgeEntity[Organisation, Taxonomy]
case class OrganisationTaxonomy()

case class RichOrganisation(organisation: Organisation with Entity, links: Map[Organisation with Entity, (String, String)]) {
  def name: String               = organisation.name
  def description: String        = organisation.description
  def taskRule: String           = organisation.taskRule
  def observableRule: String     = organisation.observableRule
  def _id: EntityId              = organisation._id
  def _createdAt: Date           = organisation._createdAt
  def _createdBy: String         = organisation._createdBy
  def _updatedAt: Option[Date]   = organisation._updatedAt
  def _updatedBy: Option[String] = organisation._updatedBy
}
