package org.thp.thehive.services

import org.thp.scalligraph.ScalligraphModule
import org.thp.scalligraph.services.GenIntegrityCheckOps
import org.thp.thehive.controllers.ModelDescription
import org.thp.thehive.models.HealthStatus
import org.thp.thehive.services.notification.notifiers.NotifierProvider
import org.thp.thehive.services.notification.triggers.TriggerProvider
import play.api.libs.json._

case class PropertyDescription(name: String, `type`: String, values: Seq[JsValue] = Nil, labels: Seq[String] = Nil)
object PropertyDescription {
  implicit val propertyDescriptionWrites: Writes[PropertyDescription] =
    Json.writes[PropertyDescription].transform((_: JsObject) + ("description" -> JsString("")))
}

case class EntityDescription(label: String, path: String, initialQuery: String, attributes: Seq[PropertyDescription])
object EntityDescription {
  implicit val entityDescriptionWrites: Writes[EntityDescription] = Json.writes[EntityDescription]
}

trait Connector extends ScalligraphModule {
  val name: String
  def status: JsObject           = Json.obj("enabled" -> true)
  def health: HealthStatus.Value = HealthStatus.Ok
  def modelDescriptions: Map[Int, ModelDescription]
  def triggerProviders: Set[TriggerProvider]
  def notifierProviders: Set[NotifierProvider]
  def integrityChecks: Set[GenIntegrityCheckOps]
}
