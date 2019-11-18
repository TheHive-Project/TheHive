package org.thp.thehive.models

import org.thp.scalligraph.VertexEntity

@VertexEntity
case class Page(title: String, content: String, slug: String, order: Int, category: String)
