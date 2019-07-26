package org.thp.thehive.models

import play.api.libs.json.{Format, Json}

object HealthStatus extends Enumeration {
  type Type = Value

  val Ok, Warning, Error = Value

  implicit val format: Format[Type] = Json.formatEnum(HealthStatus)
}
