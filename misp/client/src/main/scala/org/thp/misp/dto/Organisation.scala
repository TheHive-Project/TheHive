package org.thp.misp.dto

import play.api.libs.json.{Json, Reads}

import java.util.UUID

case class Organisation(id: String, name: String, description: String, uuid: UUID)

object Organisation {
  implicit val reads: Reads[Organisation] = Json.reads[Organisation]
}
