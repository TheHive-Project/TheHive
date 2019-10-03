package org.thp.thehive.services

import play.api.libs.json.{JsObject, Json}

import org.thp.thehive.models.HealthStatus

trait Connector {
  val name: String
  def status: JsObject           = Json.obj("enabled" -> true)
  def health: HealthStatus.Value = HealthStatus.Ok
}
