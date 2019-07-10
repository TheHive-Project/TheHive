package org.thp.thehive.dto.v0

import play.api.libs.json.{Json, OFormat, Writes}

case class InputUser(login: String, name: String, roles: Seq[String], password: Option[String], organisation: Option[String] = None)

object InputUser {
  implicit val writes: Writes[InputUser] = Json.writes[InputUser]
}

case class OutputUser( // TODO add metadata
    id: String,
    login: String,
    name: String,
    roles: Set[String],
    organisation: String,
    hasKey: Option[Boolean] = None,
    status: String = "Ok"
)

object OutputUser {
  implicit val format: OFormat[OutputUser] = Json.format[OutputUser]
}
