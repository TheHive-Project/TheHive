package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{EdgeEntity, VertexEntity}
import play.api.libs.json.JsObject

@VertexEntity
case class Dashboard(title: String, description: String, definition: JsObject)

@EdgeEntity[Dashboard, User]
case class DashboardUser()

@EdgeEntity[Organisation, Dashboard]
case class OrganisationDashboard(writable: Boolean)

case class RichDashboard(
    dashboard: Dashboard with Entity,
    organisationShares: Map[String, Boolean]
) {
  def _id: String                = dashboard._id
  def _createdBy: String         = dashboard._createdBy
  def _updatedBy: Option[String] = dashboard._updatedBy
  def _createdAt: Date           = dashboard._createdAt
  def _updatedAt: Option[Date]   = dashboard._updatedAt
  def title: String              = dashboard.title
  def description: String        = dashboard.description
  def definition: JsObject       = dashboard.definition

}
