package org.thp.thehive.models

import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity, EntityId}

import java.util.Date

@BuildEdgeEntity[Log, Attachment]
case class LogAttachment()

@DefineIndex(IndexType.fulltext, "message")
@DefineIndex(IndexType.standard, "date")
@DefineIndex(IndexType.standard, "taskId")
@DefineIndex(IndexType.standard, "organisationIds")
@BuildVertexEntity
case class Log(
    message: String,
    date: Date,
    /* filled by the service */
    taskId: EntityId = EntityId(""),
    organisationIds: Set[EntityId] = Set.empty
)

case class RichLog(log: Log with Entity, attachments: Seq[Attachment with Entity]) {
  def _id: EntityId              = log._id
  def _createdBy: String         = log._createdBy
  def _updatedBy: Option[String] = log._updatedBy
  def _createdAt: Date           = log._createdAt
  def _updatedAt: Option[Date]   = log._updatedAt
  def date: Date                 = log.date
  def message: String            = log.message
}
