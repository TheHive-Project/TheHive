package org.thp.thehive.dto.v1

import play.api.libs.json.{JsObject, Json, OFormat}

import java.util.Date

case class OutputTag(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    namespace: String,
    predicate: String,
    value: Option[String],
    description: Option[String],
    colour: String,
    extraData: JsObject
)

object OutputTag {
  implicit val format: OFormat[OutputTag] = Json.format[OutputTag]
}
