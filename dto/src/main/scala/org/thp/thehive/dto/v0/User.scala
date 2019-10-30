package org.thp.thehive.dto.v0

import java.util.Date

import play.api.libs.json.{Json, OFormat, Writes}

import org.thp.scalligraph.controllers.FFile

case class InputUser(
    login: String,
    name: String,
    roles: Seq[String],
    password: Option[String],
    organisation: Option[String] = None,
    avatar: Option[FFile]
)

object InputUser {
  implicit val writes: Writes[InputUser] = Json.writes[InputUser]
}

case class OutputUser(
    _id: String,
    id: String,
    createdBy: String,
    updatedBy: Option[String],
    createdAt: Date,
    updatedAt: Option[Date],
    _type: String,
    login: String,
    name: String,
    roles: Set[String],
    organisation: String,
    hasKey: Boolean,
    status: String
)

object OutputUser {
  implicit val format: OFormat[OutputUser] = Json.format[OutputUser]
}
