package org.thp.misp.dto

import java.util.Date

import play.api.libs.json.Reads

case class Attribute(
    id: String,
    `type`: String,
    category: String,
    toIds: Boolean,
    eventId: String,
    distribution: Int,
    date: Date,
    comment: Option[String],
    deleted: Boolean,
    data: Option[String],
    value: String, // TODO need check: option for attachment ?
    firstSeen: Option[Date],
    lastSeen: Option[Date],
    tags: Seq[String] // TODO not documented in https://github.com/MISP/misp-rfc/blob/master/misp-core-format/raw.md => check type
)

object Attribute {
  implicit val reads: Reads[Attribute] = ???
}
