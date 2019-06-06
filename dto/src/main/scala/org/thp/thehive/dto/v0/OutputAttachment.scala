package org.thp.thehive.dto.v0

import play.api.libs.json.{Json, OFormat}

case class OutputAttachment(name: String, hashes: Seq[String], size: Long, contentType: String, id: String)

object OutputAttachment {
  implicit val format: OFormat[OutputAttachment] = Json.format[OutputAttachment]
}
