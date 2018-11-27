package org.thp.thehive.models

import org.thp.scalligraph.VertexEntity

@VertexEntity
case class Observable(`type`: String, tags: Seq[String], message: Option[String], tlp: Int, pap: Int)
