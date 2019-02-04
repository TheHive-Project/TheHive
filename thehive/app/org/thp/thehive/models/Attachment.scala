package org.thp.thehive.models
import org.thp.scalligraph.{Hash, VertexEntity}

@VertexEntity
case class Attachment(name: String, size: Long, contentType: String, hashes: Seq[Hash])
