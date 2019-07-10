package org.thp.thehive.models

import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@VertexEntity
case class Dashboard(title: String, description: String, shared: Boolean, definition: String)

@EdgeEntity[Dashboard, User]
case class DashboardUser()

@EdgeEntity[Organisation, Dashboard]
case class OrganisationDashboard()
