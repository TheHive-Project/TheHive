package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.thehive.models.HealthStatus
import play.api.libs.json.{JsObject, Json}

trait Connector {
  val name: String
  def status: JsObject           = Json.obj("enabled" -> true)
  def health(implicit authContext: AuthContext): HealthStatus.Value = HealthStatus.Ok
}
