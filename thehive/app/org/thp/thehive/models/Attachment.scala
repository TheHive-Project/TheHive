package org.thp.thehive.models

import org.thp.scalligraph.BuildVertexEntity
import org.thp.scalligraph.models.{DefineIndex, IndexType}
import org.thp.scalligraph.utils.Hash

@DefineIndex(IndexType.fulltext, "name")
@DefineIndex(IndexType.standard, "size")
@DefineIndex(IndexType.fulltext, "contentType")
@DefineIndex(IndexType.standard, "hashes")
@DefineIndex(IndexType.standard, "attachmentId")
@BuildVertexEntity
case class Attachment(name: String, size: Long, contentType: String, hashes: Seq[Hash], attachmentId: String)
