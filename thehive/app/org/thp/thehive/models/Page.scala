package org.thp.thehive.models

import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity}

@BuildEdgeEntity[Organisation, Page]
case class OrganisationPage()

@BuildVertexEntity
case class Page(title: String, content: String, slug: String, order: Int, category: String)
