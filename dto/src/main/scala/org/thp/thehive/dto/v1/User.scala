package org.thp.thehive.dto.v1

import play.api.libs.json.{Format, Json, Writes}

case class InputUser(login: String, name: String, permissions: Seq[String], password: Option[String], organisation: Option[String] = None)

object InputUser {
  implicit val writes: Writes[InputUser] = Json.writes[InputUser]
}

case class OutputUser(login: String, name: String, permissions: Set[String], organisation: String)

object OutputUser {
  implicit val format: Format[OutputUser] = Json.format[OutputUser]
}
