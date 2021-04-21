package org.thp.thehive.connector.misp

import akka.actor.ActorRef
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.services.config.ConfigItem
import org.thp.scalligraph.{ActorSingletonUtils, ScalligraphApplication, ScalligraphModule}
import org.thp.thehive.TheHiveModule
import org.thp.thehive.connector.misp.controllers.v0.{MispCtrl, MispRouter}
import org.thp.thehive.connector.misp.services._
import org.thp.thehive.models.HealthStatus
import org.thp.thehive.services.{Connector => TheHiveConnector}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

sealed trait SyncInterval
sealed trait SyncInitialDelay

class MispConnector(app: ScalligraphApplication, theHiveModule: TheHiveModule)
    extends TheHiveConnector
    with ActorSingletonUtils
    with ScalligraphModule {
  def this(app: ScalligraphApplication) = this(app, app.getModule[TheHiveModule])

  import app.{executionContext, materializer, _}
  import com.softwaremill.macwire._
  import com.softwaremill.macwire.akkasupport._
  import com.softwaremill.tagging._
  import theHiveModule.applicationConfig

  override val name: String = "misp"

  val clientsConfig: ConfigItem[Seq[TheHiveMispClientConfig], Seq[TheHiveMispClient]] =
    applicationConfig.mapItem[Seq[TheHiveMispClientConfig], Seq[TheHiveMispClient]]("misp.servers", "", _.map(new TheHiveMispClient(_, materializer)))

  def clients: Seq[TheHiveMispClient] = clientsConfig.get

  val attributeConvertersConfig: ConfigItem[Seq[AttributeConverter], Seq[AttributeConverter]] =
    applicationConfig.item[Seq[AttributeConverter]]("misp.attribute.mapping", "Describe how to map MISP attribute to observable")

  val syncIntervalConfig: ConfigItem[FiniteDuration, FiniteDuration] @@ SyncInterval =
    applicationConfig.item[FiniteDuration]("misp.syncInterval", "").taggedWith[SyncInterval]

  val syncInitialDelayConfig: ConfigItem[FiniteDuration, FiniteDuration] @@ SyncInitialDelay =
    applicationConfig.item[FiniteDuration]("misp.syncInitialDelay", "").taggedWith[SyncInitialDelay]

  val statusCheckIntervalConfig: ConfigItem[FiniteDuration, FiniteDuration] =
    applicationConfig.item[FiniteDuration]("misp.checkStatusInterval", "Interval between two checks of misp status")

  def statusCheckInterval: FiniteDuration = statusCheckIntervalConfig.get

  private var cachedStatus: JsObject = Json.obj("enable" -> true, "status" -> "CHECKING")

  override def status: JsObject = cachedStatus

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
        actorSystem.scheduler.scheduleOnce(statusCheckInterval)(updateStatus())
      }

  private var cachedHealth: HealthStatus.Value = HealthStatus.Ok

  override def health: HealthStatus.Value = cachedHealth

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

        actorSystem.scheduler.scheduleOnce(statusCheckInterval)(updateHealth())
      }

  override def init(): Unit = {
    updateHealth()
    updateStatus()
    theHiveModule.connectors += this
  }

  lazy val mispImportSrv: MispImportSrv   = wire[MispImportSrv]
  lazy val mispExportSrv: MispExportSrv   = wire[MispExportSrv]
  lazy val mispCtrl: MispCtrl             = wire[MispCtrl]
  lazy val mispActor: ActorRef @@ MispTag = wireActorSingleton(actorSystem, wireProps[MispActor], "misp-actor").taggedWith[MispTag]
  routers += wire[MispRouter].withPrefix("/api/connector/misp/")
}
