package org.thp.thehive.dto.v1

import java.util.Date

import play.api.libs.json.{Json, OFormat, Writes}

import org.thp.scalligraph.controllers.FFile

case class InputUser(login: String, name: String, password: Option[String], profile: String, organisation: Option[String], avatar: Option[FFile])

object InputUser {
  implicit val writes: Writes[InputUser] = Json.writes[InputUser]
}

case class OutputUser(
    _id: String,
    _createdBy: String,
    _updatedBy: Option[String],
    _createdAt: Date,
    _updatedAt: Option[Date],
    login: String,
    name: String,
    hasKey: Boolean,
    hasPassword: Boolean,
    locked: Boolean,
    profile: String,
    permissions: Set[String],
    organisation: String,
    avatar: Option[String]
)

object OutputUser {
  implicit val format: OFormat[OutputUser] = Json.format[OutputUser]
}
