package org.thp.thehive.models

object HealthStatus extends Enumeration {
  type HealthStatus = Value
  val Ok, Warning, Error = Value
}
