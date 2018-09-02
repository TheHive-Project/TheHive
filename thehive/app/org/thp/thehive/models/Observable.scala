package org.thp.thehive.models

import org.thp.scalligraph.VertexEntity
import org.thp.scalligraph.models.{DefineIndex, IndexType}

@DefineIndex(IndexType.unique, "value")
@VertexEntity
case class Indicator(value: String)
