package org.thp.thehive.dto.v1

import java.util.Date

import play.api.libs.json.{Json, OFormat, OWrites}

case class InputProfile(name: String, permissions: Set[String])

object InputProfile {
  implicit val writes: OWrites[InputProfile] = Json.writes[InputProfile]
}

case class OutputProfile(
    _id: String,
    id: String,
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    _type: String,
    name: String,
    permissions: Seq[String]
)

object OutputProfile {
  implicit val format: OFormat[OutputProfile] = Json.format[OutputProfile]
}
