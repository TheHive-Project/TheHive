package org.thp.thehive.connector.cortex

import akka.actor.ActorRef
import org.thp.cortex.client.{CortexClient, CortexClientConfig}
import org.thp.scalligraph.query.QueryExecutor
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.services.config.ConfigItem
import org.thp.scalligraph.{ActorSingletonUtils, ScalligraphApplication, ScalligraphModule}
import org.thp.thehive.TheHiveModule
import org.thp.thehive.connector.cortex.controllers.v0._
import org.thp.thehive.connector.cortex.models.CortexSchemaDefinition
import org.thp.thehive.connector.cortex.services._
import org.thp.thehive.models.HealthStatus
import org.thp.thehive.services.{ReportTagSrv, Connector => TheHiveConnector}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class CortexModule(app: ScalligraphApplication, theHiveModule: TheHiveModule)
    extends TheHiveConnector
    with ScalligraphModule
    with ActorSingletonUtils {

  def this(app: ScalligraphApplication) = this(app, app.getModule[TheHiveModule])
  import app._
  import com.softwaremill.macwire._
  import com.softwaremill.macwire.akkasupport._
  import com.softwaremill.tagging._

  override val name: String = "cortex"

  lazy val clientsConfig: ConfigItem[Seq[CortexClientConfig], Seq[CortexClient]] =
    applicationConfig.mapItem[Seq[CortexClientConfig], Seq[CortexClient]](
      "cortex.servers",
      "",
      _.map(new CortexClient(_, materializer, executionContext))
    )
  def clients: Seq[CortexClient] = clientsConfig.get

  lazy val statusCheckIntervalConfig: ConfigItem[FiniteDuration, FiniteDuration] =
    applicationConfig.item[FiniteDuration]("cortex.statusCheckInterval", "Interval between two checks of cortex status")
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

        actorSystem.scheduler.scheduleOnce(statusCheckInterval)(updateHealth())
      }

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

        actorSystem.scheduler.scheduleOnce(statusCheckInterval)(updateStatus())
      }

  override def init(): Unit = {
    updateStatus()
    updateHealth()
    theHiveModule.connectors += this
  }

  lazy val cortexActor: ActorRef @@ CortexTag = wireActorSingleton(actorSystem, wireProps[CortexActor], "cortex-actor").taggedWith[CortexTag]

  lazy val jobCtrl: JobCtrl                               = wire[JobCtrl]
  lazy val publicJob: PublicJob                           = wire[PublicJob]
  lazy val analyzerCtrl: AnalyzerCtrl                     = wire[AnalyzerCtrl]
  lazy val publicAnalyzerTemplate: PublicAnalyzerTemplate = wire[PublicAnalyzerTemplate]
  lazy val actionCtrl: ActionCtrl                         = wire[ActionCtrl]
  lazy val publicAction: PublicAction                     = wire[PublicAction]
  lazy val analyzerTemplateCtrl: AnalyzerTemplateCtrl     = wire[AnalyzerTemplateCtrl]
  lazy val responderCtrl: ResponderCtrl                   = wire[ResponderCtrl]

  lazy val jobSrv: JobSrv                           = wire[JobSrv]
  lazy val analyzerSrv: AnalyzerSrv                 = wire[AnalyzerSrv]
  lazy val analyzerTemplateSrv: AnalyzerTemplateSrv = wire[AnalyzerTemplateSrv]
  lazy val actionSrv: ActionSrv                     = wire[ActionSrv]
  lazy val serviceHelper: ServiceHelper             = wire[ServiceHelper]
  lazy val entityHelper: EntityHelper               = wire[EntityHelper]
  lazy val cortexAuditSrv: CortexAuditSrv           = wire[CortexAuditSrv]
  lazy val responderSrv: ResponderSrv               = wire[ResponderSrv]
  lazy val reportTagSrv: ReportTagSrv               = wire[ReportTagSrv]
  lazy val actionOperationSrv: ActionOperationSrv   = wire[ActionOperationSrv]

  lazy val queryExecutor: QueryExecutor = wire[CortexQueryExecutor]
  routers += wire[CortexRouter].withPrefix("/api/connector/cortex")
  queryExecutors += queryExecutor
  schemas += CortexSchemaDefinition
  lazy val cortexModelDescription: CortexModelDescription = wire[CortexModelDescription]
  theHiveModule.entityDescriptions += (0 -> cortexModelDescription)
  theHiveModule.entityDescriptions += (1 -> cortexModelDescription)
}
