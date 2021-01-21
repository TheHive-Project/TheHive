package org.thp.thehive.connector.misp.services

import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.models.{HealthStatus, ObservableType}
import org.thp.thehive.services.{Connector => TheHiveConnector}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Connector @Inject() (appConfig: ApplicationConfig, system: ActorSystem, mat: Materializer, implicit val ec: ExecutionContext)
    extends TheHiveConnector {
  override val name: String = "misp"

  val clientsConfig: ConfigItem[Seq[TheHiveMispClientConfig], Seq[TheHiveMispClient]] =
    appConfig.mapItem[Seq[TheHiveMispClientConfig], Seq[TheHiveMispClient]]("misp.servers", "", _.map(new TheHiveMispClient(_, mat)))
  def clients: Seq[TheHiveMispClient] = clientsConfig.get

  val attributeConvertersConfig: ConfigItem[Seq[AttributeConverter], Seq[AttributeConverter]] =
    appConfig.item[Seq[AttributeConverter]]("misp.attribute.mapping", "Describe how to map MISP attribute to observable")

  def attributeConverter(attributeCategory: String, attributeType: String): Option[AttributeConverter] =
    attributeConvertersConfig.get.reverseIterator.find(a => a.mispCategory == attributeCategory && a.mispType == attributeType)

  def attributeConverter(observableType: String): Option[(String, String)] =
    attributeConvertersConfig.get.reverseIterator.find(_.`type`.value == observableType).map(a => a.mispCategory -> a.mispType)

  val syncIntervalConfig: ConfigItem[FiniteDuration, FiniteDuration] = appConfig.item[FiniteDuration]("misp.syncInterval", "")
  def syncInterval: FiniteDuration                                   = syncIntervalConfig.get

  val syncInitialDelayConfig: ConfigItem[FiniteDuration, FiniteDuration] = appConfig.item[FiniteDuration]("misp.syncInitialDelay", "")
  val syncInitialDelay: FiniteDuration                                   = syncInitialDelayConfig.get

  val statusCheckIntervalConfig: ConfigItem[FiniteDuration, FiniteDuration] =
    appConfig.item[FiniteDuration]("misp.checkStatusInterval", "Interval between two checks of misp status")
  def statusCheckInterval: FiniteDuration = statusCheckIntervalConfig.get

  private var cachedStatus: JsObject = Json.obj("enable" -> true, "status" -> "CHECKING")
  override def status: JsObject      = cachedStatus
  private def updateStatus(): Unit =
    Future
      .traverse(clients)(client => client.getStatus)
      .foreach { statusDetails =>
        val distinctStatus = statusDetails.map(s => (s \ "status").as[String]).toSet
        val healthStatus =
          if (distinctStatus.contains("OK"))
            if (distinctStatus.size > 1) "WARNING" else "OK"
          else "ERROR"
        cachedStatus = Json.obj("enabled" -> true, "servers" -> statusDetails, "status" -> healthStatus)
        system.scheduler.scheduleOnce(statusCheckInterval)(updateStatus())
      }
  updateStatus()

  private var cachedHealth: HealthStatus.Value = HealthStatus.Ok
  override def health: HealthStatus.Value      = cachedHealth
  private def updateHealth(): Unit =
    Future
      .traverse(clients)(_.getHealth)
      .foreach { healthStatus =>
        val distinctStatus = healthStatus.toSet
        cachedHealth =
          if (distinctStatus.contains(HealthStatus.Ok))
            if (distinctStatus.size > 1) HealthStatus.Warning else HealthStatus.Ok
          else if (distinctStatus.contains(HealthStatus.Error)) HealthStatus.Error
          else HealthStatus.Warning

        system.scheduler.scheduleOnce(statusCheckInterval)(updateHealth())
      }
  updateHealth()
}
