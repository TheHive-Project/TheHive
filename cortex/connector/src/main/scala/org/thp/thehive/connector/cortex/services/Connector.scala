package org.thp.thehive.connector.cortex.services

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.thp.cortex.client.{CortexClient, CortexClientConfig}
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.models.HealthStatus
import org.thp.thehive.services.{Connector => TheHiveConnector}
import play.api.libs.json.{JsObject, Json}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class Connector @Inject() (
    appConfig: ApplicationConfig,
    mat: Materializer,
    implicit val system: ActorSystem,
    implicit val ec: ExecutionContext
) extends TheHiveConnector {
  override val name: String = "cortex"

  val clientsConfig: ConfigItem[Seq[CortexClientConfig], Seq[CortexClient]] =
    appConfig.mapItem[Seq[CortexClientConfig], Seq[CortexClient]]("cortex.servers", "", _.map(new CortexClient(_, mat, ec)))
  def clients: Seq[CortexClient]                                     = clientsConfig.get
  val refreshDelayConfig: ConfigItem[FiniteDuration, FiniteDuration] = appConfig.item[FiniteDuration]("cortex.refreshDelay", "")
  def refreshDelay: FiniteDuration                                   = refreshDelayConfig.get
  val maxRetryOnErrorConfig: ConfigItem[Int, Int]                    = appConfig.item[Int]("cortex.maxRetryOnError", "")
  def maxRetryOnError: Int                                           = maxRetryOnErrorConfig.get

  val statusCheckIntervalConfig: ConfigItem[FiniteDuration, FiniteDuration] =
    appConfig.item[FiniteDuration]("cortex.statusCheckInterval", "Interval between two checks of cortex status")
  def statusCheckInterval: FiniteDuration = statusCheckIntervalConfig.get
  var cachedHealth: HealthStatus.Value    = HealthStatus.Ok
  override def health: HealthStatus.Value = cachedHealth
  var cachedStatus: JsObject              = JsObject.empty
  override def status: JsObject           = cachedStatus

  protected def updateHealth(): Unit =
    Future
      .traverse(clients)(_.getHealth)
      .foreach { healthStatus =>
        val distinctStatus = healthStatus.toSet.map(HealthStatus.withName)
        cachedHealth =
          if (distinctStatus.contains(HealthStatus.Ok))
            if (distinctStatus.size > 1) HealthStatus.Warning else HealthStatus.Ok
          else if (distinctStatus.contains(HealthStatus.Error)) HealthStatus.Error
          else HealthStatus.Warning

        system.scheduler.scheduleOnce(statusCheckInterval)(updateHealth())
      }
  updateHealth()

  protected def updateStatus(): Unit =
    Future
      .traverse(clients) { client =>
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
        val healthStatus =
          if (distinctStatus.contains("OK"))
            if (distinctStatus.size > 1) "WARNING" else "OK"
          else "ERROR"

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
