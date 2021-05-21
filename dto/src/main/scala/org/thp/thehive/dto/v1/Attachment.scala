package org.thp.thehive.dto.v1

import org.thp.thehive.dto.String128
import play.api.libs.json.{Json, OFormat, Writes}
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputAttachment(name: String128, contentType: String128, id: String128)

object InputAttachment {
  implicit val writes: Writes[InputAttachment] = Json.writes[InputAttachment]
}

case class OutputAttachment(name: String, hashes: Seq[String], size: Long, contentType: String, id: String)

object OutputAttachment {
  implicit val format: OFormat[OutputAttachment] = Json.format[OutputAttachment]
}
