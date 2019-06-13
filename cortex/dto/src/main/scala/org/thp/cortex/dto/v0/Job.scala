package org.thp.cortex.dto.v0

import play.api.libs.json.{Json, OFormat}

case class OutputJob(something: String) // TODO

object OutputJob {
  implicit val format: OFormat[OutputJob] = Json.format[OutputJob]
}
