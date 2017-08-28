package connectors.misp

import java.util.Date
import javax.inject.{ Inject, Provider, Singleton }

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.api.libs.json._

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import models.{ Alert, AlertStatus }
import services.{ AlertSrv, ArtifactSrv, CaseSrv, UserSrv }
import JsonFormat.mispAlertWrites

import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ AuthContext, MigrationSrv, TempSrv }

@Singleton
class MispSynchro @Inject() (
    mispConfig: MispConfig,
    migrationSrv: MigrationSrv,
    mispSrv: MispSrv,
    caseSrv: CaseSrv,
    artifactSrv: ArtifactSrv,
    alertSrvProvider: Provider[AlertSrv],
    userSrv: UserSrv,
    tempSrv: TempSrv,
    lifecycle: ApplicationLifecycle,
    system: ActorSystem,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  private[misp] lazy val logger = Logger(getClass)
  private[misp] lazy val alertSrv = alertSrvProvider.get

  private[misp] def initScheduler(): Unit = {
    val task = system.scheduler.schedule(0.seconds, mispConfig.interval) {
      if (migrationSrv.isReady) {
        logger.info("Update of MISP events is starting ...")
        userSrv
          .inInitAuthContext { implicit authContext ⇒
            synchronize().andThen { case _ ⇒ tempSrv.releaseTemporaryFiles() }
          }
          .onComplete {
            case Success(a) ⇒
              logger.info("Misp synchronization completed")
              a.collect {
                case Failure(t) ⇒ logger.warn(s"Update MISP error", t)
              }
            case Failure(t) ⇒ logger.info("Misp synchronization failed", t)
          }
      }
      else {
        logger.info("MISP synchronization cancel, database is not ready")
      }
    }
    lifecycle.addStopHook { () ⇒
      logger.info("Stopping MISP fetching ...")
      task.cancel()
      Future.successful(())
    }
  }

  initScheduler()

  def synchronize()(implicit authContext: AuthContext): Future[Seq[Try[Alert]]] = {
    import org.elastic4play.services.QueryDSL._

    // for each MISP server
    Source(mispConfig.connections.toList)
      // get last synchronization
      .mapAsyncUnordered(1) { mispConnection ⇒
        alertSrv.stats(and("type" ~= "misp", "source" ~= mispConnection.name), Seq(selectMax("lastSyncDate")))
          .map { maxLastSyncDate ⇒ mispConnection → new Date((maxLastSyncDate \ "max_lastSyncDate").as[Long]) }
          .recover { case _ ⇒ mispConnection → new Date(0) }
      }
      .flatMapConcat {
        case (mispConnection, lastSyncDate) ⇒
          synchronize(mispConnection, Some(lastSyncDate))
      }
      .runWith(Sink.seq)
  }

  def fullSynchronize()(implicit authContext: AuthContext): Future[immutable.Seq[Try[Alert]]] = {
    Source(mispConfig.connections.toList)
      .flatMapConcat(mispConnection ⇒ synchronize(mispConnection, None))
      .runWith(Sink.seq)
  }

  def synchronize(mispConnection: MispConnection, lastSyncDate: Option[Date])(implicit authContext: AuthContext): Source[Try[Alert], NotUsed] = {
    logger.info(s"Synchronize MISP ${mispConnection.name} from $lastSyncDate")
    // get events that have been published after the last synchronization
    mispSrv.getEventsFromDate(mispConnection, lastSyncDate.getOrElse(new Date(0)))
      // get related alert
      .mapAsyncUnordered(1) { event ⇒
        logger.trace(s"Looking for alert misp:${event.source}:${event.sourceRef}")
        alertSrv.get("misp", event.source, event.sourceRef)
          .map((event, _))
      }
      .mapAsyncUnordered(1) {
        case (event, alert) ⇒
          logger.trace(s"MISP synchro ${mispConnection.name}, event ${event.sourceRef}, alert ${alert.fold("no alert")(a ⇒ "alert " + a.alertId() + "last sync at " + a.lastSyncDate())}")
          logger.info(s"getting MISP event ${event.source}:${event.sourceRef}")
          mispSrv.getAttributes(mispConnection, event.sourceRef, lastSyncDate.flatMap(_ ⇒ alert.map(_.lastSyncDate())))
            .map((event, alert, _))
      }
      .mapAsyncUnordered(1) {
        // if there is no related alert, create a new one
        case (event, None, attrs) ⇒
          logger.info(s"MISP event ${event.source}:${event.sourceRef} has no related alert, create it with ${attrs.size} observable(s)")
          val alertJson = Json.toJson(event).as[JsObject] +
            ("type" → JsString("misp")) +
            ("caseTemplate" → mispConnection.caseTemplate.fold[JsValue](JsNull)(JsString)) +
            ("artifacts" → JsArray(attrs))
          alertSrv.create(Fields(alertJson))
            .map(Success(_))
            .recover { case t ⇒ Failure(t) }

        // if a related alert exists and we follow it or a fullSync, update it
        case (event, Some(alert), attrs) if alert.follow() || lastSyncDate.isEmpty ⇒
          logger.info(s"MISP event ${event.source}:${event.sourceRef} has related alert, update it with ${attrs.size} observable(s)")
          val alertJson = Json.toJson(event).as[JsObject] -
            "type" -
            "source" -
            "sourceRef" -
            "caseTemplate" -
            "date" +
            ("artifacts" → JsArray(attrs)) +
            // if this is a full synchronization,  don't update alert status
            ("status" → (if (lastSyncDate.isEmpty) Json.toJson(alert.status())
            else alert.status() match {
              case AlertStatus.New ⇒ Json.toJson(AlertStatus.New)
              case _               ⇒ Json.toJson(AlertStatus.Updated)
            }))
          logger.debug(s"Update alert ${alert.id} with\n$alertJson")
          val fAlert = alertSrv.update(alert.id, Fields(alertJson))
          // if a case have been created, update it
          (alert.caze() match {
            case None ⇒ fAlert
            case Some(caze) ⇒
              for {
                a ← fAlert
                // if this is a full synchronization, don't update case status
                caseFields = if (lastSyncDate.isEmpty) Fields(alert.toCaseJson).unset("status")
                else Fields(alert.toCaseJson)
                _ ← caseSrv.update(caze, caseFields)
                _ ← artifactSrv.create(caze, attrs.map(Fields.apply))
              } yield a
          })
            .map(Success(_))
            .recover { case t ⇒ Failure(t) }

        // if the alert is not followed, do nothing
        case (_, Some(alert), _) ⇒
          Future.successful(Success(alert))
      }
  }
}
