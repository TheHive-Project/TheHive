package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{EdgeEntity, VertexEntity}
import play.api.libs.json.{Json, OFormat}

@EdgeEntity[Log, Attachment]
case class LogAttachment()

@VertexEntity
case class Log(message: String, date: Date, deleted: Boolean)

object Log {
  implicit val format: OFormat[Log] = Json.format[Log]
}

case class RichLog(log: Log with Entity, attachments: Seq[Attachment with Entity]) {
  val _id: String                = log._id
  val _createdBy: String         = log._createdBy
  val _updatedBy: Option[String] = log._updatedBy
  val _createdAt: Date           = log._createdAt
  val _updatedAt: Option[Date]   = log._updatedAt
  val date: Date                 = log.date
  val message: String            = log.message
  val deleted: Boolean           = log.deleted
}
