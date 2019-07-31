package org.thp.thehive.connector.cortex.services

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexConfig
import org.thp.scalligraph.services.{ApplicationConfiguration, ConfigItem}
import org.thp.thehive.models.HealthStatus
import org.thp.thehive.services.{Connector => TheHiveConnector}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class Connector @Inject()(
    cortexConfig: CortexConfig,
    appConfig: ApplicationConfiguration,
    implicit val ec: ExecutionContext,
    implicit val system: ActorSystem
) extends TheHiveConnector {
  override val name: String = "cortex"

  val statusCheckIntervalConfig: ConfigItem[FiniteDuration] =
    appConfig.item[FiniteDuration]("cortex.statusCheckInterval", "Interval between two checks of cortex status")
  def statusCheckInterval: FiniteDuration = statusCheckIntervalConfig.get

  override def health: HealthStatus.Value = cachedHealth
  var cachedHealth: HealthStatus.Value    = HealthStatus.Ok
  private def updateHealth(): Unit =
    Future
      .traverse(cortexConfig.instances.values)(_.getHealth)
      .foreach { healthStatus =>
        val distinctStatus = healthStatus.toSet.map(HealthStatus.withName)
        cachedHealth = if (distinctStatus.contains(HealthStatus.Ok)) {
          if (distinctStatus.size > 1) HealthStatus.Warning else HealthStatus.Ok
        } else if (distinctStatus.contains(HealthStatus.Error)) HealthStatus.Error
        else HealthStatus.Warning
        system.scheduler.scheduleOnce(statusCheckInterval)(updateHealth())
      }

  override def status: JsObject = cachedStatus
  var cachedStatus: JsObject    = JsObject.empty
  private def updateStatus(): Unit =
    Future
      .traverse(cortexConfig.instances.values) { client =>
        client.getVersion.transformWith {
          case Success(version) =>
            client.getCurrentUser.transform {
              case _: Success[_] => Success((client.name, version, "OK"))
              case _: Failure[_] => Success((client.name, version, "AUTH_ERROR"))
            }
          case _: Failure[_] => Future.successful((client.name, "", "ERROR"))
        }
      }
      .foreach { statusDetails =>
        val distinctStatus = statusDetails.map(_._3).toSet
        val healthStatus = if (distinctStatus.contains("OK")) {
          if (distinctStatus.size > 1) "WARNING" else "OK"
        } else "ERROR"
        cachedStatus = Json.obj(
          "enabled" -> true,
          "status"  -> healthStatus,
          "servers" -> statusDetails.map {
            case (n, v, s) => Json.obj("name" -> n, "version" -> v, "status" -> s)
          }
        )
        system.scheduler.scheduleOnce(statusCheckInterval)(updateStatus())
      }
  updateStatus()

}
