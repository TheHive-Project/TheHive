package org.thp.thehive.models

import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@EdgeEntity[Observable, Indicator]
case class ObservableIndicator()

@VertexEntity
case class Observable(`type`: String, tags: Seq[String], message: Option[String], tlp: Int, pap: Int)
