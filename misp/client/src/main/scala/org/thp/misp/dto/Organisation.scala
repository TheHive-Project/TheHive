package org.thp.misp.dto

import java.util.UUID

import play.api.libs.json.{Json, Reads}

case class Organisation(id: String, name: String, description: String, uuid: UUID)

object Organisation {
  implicit val reads: Reads[Organisation] = Json.reads[Organisation]
}
