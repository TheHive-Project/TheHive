package org.thp.thehive.dto.v1

import java.util.Date

import play.api.libs.json.{Json, OFormat, Writes}

case class InputUser(login: String, name: String, password: Option[String], profile: String, organisation: Option[String] = None)

object InputUser {
  implicit val writes: Writes[InputUser] = Json.writes[InputUser]
}

case class OutputUser(
    _id: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    login: String,
    name: String,
    hasKey: Boolean,
    hasPassword: Boolean,
    locked: Boolean,
    profile: String,
    permissions: Set[String],
    organisation: String
)

object OutputUser {
  implicit val format: OFormat[OutputUser] = Json.format[OutputUser]
}
