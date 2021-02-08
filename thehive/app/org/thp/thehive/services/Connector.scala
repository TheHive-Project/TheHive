package org.thp.thehive.services

import org.thp.scalligraph.models.SchemaStatus
import org.thp.thehive.models.HealthStatus
import play.api.libs.json.{JsObject, Json}

trait Connector {
  val name: String
  def status: JsObject                   = Json.obj("enabled" -> true)
  def health: HealthStatus.Value         = HealthStatus.Ok
  def schemaStatus: Option[SchemaStatus] = None
}
