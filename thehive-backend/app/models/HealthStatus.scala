package models

import org.elastic4play.models.HiveEnumeration

object HealthStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Ok, Warning, Error = Value
}