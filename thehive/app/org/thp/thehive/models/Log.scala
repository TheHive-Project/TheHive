package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@EdgeEntity[Log, Attachment]
case class LogAttachment()

@VertexEntity
case class Log(message: String, date: Date, deleted: Boolean)
