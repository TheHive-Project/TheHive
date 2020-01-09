package org.thp.thehive.models

import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@EdgeEntity[Organisation, Page]
case class OrganisationPage()

@VertexEntity
case class Page(title: String, content: String, slug: String, order: Int, category: String)
