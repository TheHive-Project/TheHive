package org.thp.thehive.dto.v0

import play.api.libs.json.{Json, OFormat}

case class OutputPermission(name: String, label: String, scope: Seq[String])

object OutputPermission {
  implicit val format: OFormat[OutputPermission] = Json.format[OutputPermission]
}
