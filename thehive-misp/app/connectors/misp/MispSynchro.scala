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
import connectors.misp.JsonFormat.mispArtifactWrites
import models.{ Alert, AlertStatus, Artifact, CaseStatus }
import services.{ AlertSrv, ArtifactSrv, CaseSrv, UserSrv }
import JsonFormat.mispAlertWrites

import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ Attachment, AuthContext, MigrationSrv, TempSrv }

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

  def updateArtifacts(mispConnection: MispConnection, caseId: String, mispArtifacts: Seq[MispArtifact])(implicit authContext: AuthContext): Future[Seq[Try[Artifact]]] = {
    import org.elastic4play.services.QueryDSL._

    for {
      // Either data or filename
      existingArtifacts: Seq[Either[String, String]] ← artifactSrv.find(and(withParent("case", caseId), "status" ~= "Ok"), Some("all"), Nil)._1.map { artifact ⇒
        artifact.data().map(Left.apply).getOrElse(Right(artifact.attachment().get.name))
      }
        .runWith(Sink.seq)
      newAttributes ← Future.traverse(mispArtifacts) {
        case artifact @ MispArtifact(SimpleArtifactData(data), _, _, _, _, _) if !existingArtifacts.contains(Right(data))                                ⇒ Future.successful(Fields(Json.toJson(artifact).as[JsObject]))
        case artifact @ MispArtifact(AttachmentArtifact(Attachment(filename, _, _, _, _)), _, _, _, _, _) if !existingArtifacts.contains(Left(filename)) ⇒ Future.successful(Fields(Json.toJson(artifact).as[JsObject]))
        case artifact @ MispArtifact(RemoteAttachmentArtifact(filename, reference, tpe), _, _, _, _, _) if !existingArtifacts.contains(Left(filename)) ⇒
          mispSrv.downloadAttachment(mispConnection, reference)
            .map {
              case fiv if tpe == "malware-sample" ⇒ mispSrv.extractMalwareAttachment(fiv)
              case fiv                            ⇒ fiv
            }
            .map(fiv ⇒ Fields(Json.toJson(artifact).as[JsObject]).unset("remoteAttachment").set("attachment", fiv))
        case _ ⇒ Future.successful(Fields.empty)
      }
      createdArtifacts ← artifactSrv.create(caseId, newAttributes.filterNot(_.isEmpty))
    } yield createdArtifacts
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
          mispSrv.getAttributesFromMisp(mispConnection, event.sourceRef, lastSyncDate.flatMap(_ ⇒ alert.map(_.lastSyncDate())))
            .map((event, alert, _))
      }
      .mapAsyncUnordered(1) {
        // if there is no related alert, create a new one
        case (event, None, attrs) ⇒
          logger.info(s"MISP event ${event.source}:${event.sourceRef} has no related alert, create it with ${attrs.size} observable(s)")
          val alertJson = Json.toJson(event).as[JsObject] +
            ("title" → JsString(s"#${event.sourceRef} ${event.title}")) +
            ("type" → JsString("misp")) +
            ("caseTemplate" → mispConnection.caseTemplate.fold[JsValue](JsNull)(JsString)) +
            ("artifacts" → Json.toJson(attrs))
          alertSrv.create(Fields(alertJson))
            .map(Success(_))
            .recover { case t ⇒ Failure(t) }

        case (event, Some(alert), attrs) ⇒
          logger.info(s"MISP event ${event.source}:${event.sourceRef} has related alert, update it with ${attrs.size} observable(s)")

          alert.caze().fold[Future[Boolean]](Future.successful(lastSyncDate.isDefined && attrs.nonEmpty && alert.follow())) {
            case caze if alert.follow() ⇒
              for {
                addedArtifacts ← updateArtifacts(mispConnection, caze, attrs)
                updateStatus = lastSyncDate.nonEmpty && addedArtifacts.exists(_.isSuccess)
                _ ← if (updateStatus) caseSrv.update(caze, Fields.empty.set("status", CaseStatus.Open.toString)) else Future.successful(())
              } yield updateStatus
            case _ ⇒ Future.successful(false)
          }
            .flatMap { updateStatus ⇒
              val artifacts = JsArray(alert.artifacts() ++ attrs.map(Json.toJson(_)))
              val alertJson = Json.toJson(event).as[JsObject] -
                "type" -
                "source" -
                "sourceRef" -
                "caseTemplate" -
                "date" +
                ("artifacts" → artifacts) +
                ("status" → (if (!updateStatus) Json.toJson(alert.status())
                else alert.status() match {
                  case AlertStatus.New ⇒ Json.toJson(AlertStatus.New)
                  case _               ⇒ Json.toJson(AlertStatus.Updated)
                }))
              logger.debug(s"Update alert ${alert.id} with\n$alertJson")
              alertSrv.update(alert.id, Fields(alertJson))
            }
            .map(Success(_))
            .recover { case t ⇒ Failure(t) }
      }
  }
}
