package org.thp.thehive.connector.misp

import akka.actor.ActorRef
import org.thp.scalligraph.auth.AuthSrvProvider
import org.thp.scalligraph.models.UpdatableSchema
import org.thp.scalligraph.query.QueryExecutor
import org.thp.scalligraph.services.GenIntegrityCheckOps
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.services.config.ConfigItem
import org.thp.thehive.connector.misp.controllers.v0.{MispCtrl, MispRouter}
import org.thp.thehive.connector.misp.services._
import org.thp.thehive.controllers.ModelDescription
import org.thp.thehive.models.HealthStatus
import org.thp.thehive.services.notification.notifiers.NotifierProvider
import org.thp.thehive.services.notification.triggers.TriggerProvider
import org.thp.thehive.services.{Connector => TheHiveConnector}
import play.api.libs.json.{JsObject, Json}
import play.api.routing.Router

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

object MispConnector extends TheHiveConnector {

  import com.softwaremill.macwire._
  import com.softwaremill.macwire.akkasupport._
  import com.softwaremill.tagging._
  import org.thp.thehive.TheHiveModule._
  import scalligraphApplication._

  override val name: String = "misp"

  val clientsConfig: ConfigItem[Seq[TheHiveMispClientConfig], Seq[TheHiveMispClient]] =
    applicationConfig.mapItem[Seq[TheHiveMispClientConfig], Seq[TheHiveMispClient]]("misp.servers", "", _.map(new TheHiveMispClient(_, materializer)))

  def clients: Seq[TheHiveMispClient] = clientsConfig.get

  val attributeConvertersConfig: ConfigItem[Seq[AttributeConverter], Seq[AttributeConverter]] =
    applicationConfig.item[Seq[AttributeConverter]]("misp.attribute.mapping", "Describe how to map MISP attribute to observable")

  def attributeConverter(attributeCategory: String, attributeType: String): Option[AttributeConverter] =
    attributeConvertersConfig.get.reverseIterator.find(a => a.mispCategory == attributeCategory && a.mispType == attributeType)

  def attributeConverter(observableType: String): Option[(String, String)] =
    attributeConvertersConfig.get.reverseIterator.find(_.`type`.value == observableType).map(a => a.mispCategory -> a.mispType)

  val syncIntervalConfig: ConfigItem[FiniteDuration, FiniteDuration] = applicationConfig.item[FiniteDuration]("misp.syncInterval", "")

  def syncInterval: FiniteDuration = syncIntervalConfig.get

  val syncInitialDelayConfig: ConfigItem[FiniteDuration, FiniteDuration] = applicationConfig.item[FiniteDuration]("misp.syncInitialDelay", "")
  val syncInitialDelay: FiniteDuration                                   = syncInitialDelayConfig.get

  val statusCheckIntervalConfig: ConfigItem[FiniteDuration, FiniteDuration] =
    applicationConfig.item[FiniteDuration]("misp.checkStatusInterval", "Interval between two checks of misp status")

  def statusCheckInterval: FiniteDuration = statusCheckIntervalConfig.get

  private var cachedStatus: JsObject = Json.obj("enable" -> true, "status" -> "CHECKING")

  override def status: JsObject = cachedStatus

  private def updateStatus(): Unit = {
    implicit val ec: ExecutionContext = executionContext
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
  }

  private var cachedHealth: HealthStatus.Value = HealthStatus.Ok

  override def health: HealthStatus.Value = cachedHealth

  private def updateHealth(): Unit = {
    implicit val ec: ExecutionContext = executionContext
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
  }

  override def init(): Unit = {
    updateHealth()
    updateStatus()
  }

  lazy val mispImportSrv: MispImportSrv   = wire[MispImportSrv]
  lazy val mispExportSrv: MispExportSrv   = wire[MispExportSrv]
  lazy val mispCtrl: MispCtrl             = wire[MispCtrl]
  lazy val mispActor: ActorRef @@ MispTag = wireActorSingleton(wireProps[MispActor], "misp-actor").taggedWith[MispTag]
  override lazy val routers: Set[Router]  = Set(wire[MispRouter].withPrefix("/api/connector/misp/"))

  override val queryExecutors: Set[QueryExecutor]            = Set.empty
  override val schemas: Set[UpdatableSchema]                 = Set.empty
  override val authSrvProviders: Set[AuthSrvProvider]        = Set.empty
  override val modelDescriptions: Map[Int, ModelDescription] = Map.empty
  override val triggerProviders: Set[TriggerProvider]        = Set.empty
  override val notifierProviders: Set[NotifierProvider]      = Set.empty
  override val integrityChecks: Set[GenIntegrityCheckOps]    = Set.empty
}
