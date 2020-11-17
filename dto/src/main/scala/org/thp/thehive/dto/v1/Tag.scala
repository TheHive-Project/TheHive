package org.thp.thehive.dto.v1

import play.api.libs.json.{Json, OFormat}

case class OutputTag(
  namespace: String,
  predicate: String,
  value: Option[String],
  description: Option[String],
  colour: Int
)

object OutputTag {
  implicit val format: OFormat[OutputTag] = Json.format[OutputTag]
}
