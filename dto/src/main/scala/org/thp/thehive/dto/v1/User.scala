package org.thp.thehive.dto.v1
import play.api.libs.json.{Format, Json}

case class InputUser(login: String, name: String, permissions: Seq[String], password: Option[String])
object InputUser {
  implicit val format: Format[InputUser] = Json.format[InputUser]
}

case class OutputUser(login: String, name: String, permissions: Set[String])

object OutputUser {
  implicit val format: Format[OutputUser] = Json.format[OutputUser]
}
