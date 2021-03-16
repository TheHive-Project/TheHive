package org.thp.thehive.models

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity, EntityIdOrName}
import play.api.libs.json.JsObject

import java.util.Date

@BuildVertexEntity
case class Dashboard(title: String, description: String, definition: JsObject)

@BuildEdgeEntity[Dashboard, User]
case class DashboardUser()

@BuildEdgeEntity[Organisation, Dashboard]
case class OrganisationDashboard(writable: Boolean)

case class RichDashboard(
    dashboard: Dashboard with Entity,
    organisationShares: Map[String, Boolean]
) {
  def _id: EntityIdOrName        = dashboard._id
  def _createdBy: String         = dashboard._createdBy
  def _updatedBy: Option[String] = dashboard._updatedBy
  def _createdAt: Date           = dashboard._createdAt
  def _updatedAt: Option[Date]   = dashboard._updatedAt
  def title: String              = dashboard.title
  def description: String        = dashboard.description
  def definition: JsObject       = dashboard.definition

}
