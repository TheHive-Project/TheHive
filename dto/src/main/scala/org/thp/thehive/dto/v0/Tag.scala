package org.thp.thehive.dto.v0

import org.thp.thehive.dto.{Color, Description, String128}
import play.api.libs.json.{Json, OFormat, OWrites}
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputTag(
    namespace: String128,
    predicate: String128,
    value: Option[String128],
    description: Option[Description],
    colour: Option[Color]
)

object InputTag {
  implicit val writes: OWrites[InputTag] = Json.writes[InputTag]
}

case class OutputTag(namespace: String, predicate: String, value: Option[String], description: Option[String], colour: String)

object OutputTag {
  implicit val format: OFormat[OutputTag] = Json.format[OutputTag]
}
