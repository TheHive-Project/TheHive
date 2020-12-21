package org.thp.thehive.dto.v0

import play.api.libs.json.{Json, OFormat, Writes}

case class InputAttachment(name: String, contentType: String, id: String)

object InputAttachment {
  implicit val writes: Writes[InputAttachment] = Json.writes[InputAttachment]
}

case class OutputAttachment(name: String, hashes: Seq[String], size: Long, contentType: String, id: String)

object OutputAttachment {
  implicit val format: OFormat[OutputAttachment] = Json.format[OutputAttachment]
}
