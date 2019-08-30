package org.thp.thehive.models

import org.thp.scalligraph.VertexEntity
import org.thp.scalligraph.utils.Hash

@VertexEntity
case class Attachment(name: String, size: Long, contentType: String, hashes: Seq[Hash], attachmentId: String)
