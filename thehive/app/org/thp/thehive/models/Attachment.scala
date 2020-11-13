package org.thp.thehive.models

import org.thp.scalligraph.BuildVertexEntity
import org.thp.scalligraph.utils.Hash

@BuildVertexEntity
case class Attachment(name: String, size: Long, contentType: String, hashes: Seq[Hash], attachmentId: String)
