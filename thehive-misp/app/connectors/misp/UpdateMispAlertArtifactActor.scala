package connectors.misp

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

import play.api.Logger

import akka.actor.Actor
import models.UpdateMispAlertArtifact
import services.UserSrv

import org.elastic4play.services.EventSrv

/**
 * This actor listens message from migration (message UpdateMispAlertArtifact) which indicates that artifacts in
 * MISP event must be retrieved in inserted in alerts.
 *
 * @param eventSrv event bus used to receive migration message
 * @param userSrv user service used to do operations on database without real user request
 * @param mispSrv misp service to invoke artifact update action
 * @param ec execution context
 */
@Singleton
class UpdateMispAlertArtifactActor @Inject() (
    eventSrv: EventSrv,
    userSrv: UserSrv,
    mispSrv: MispSrv,
    implicit val ec: ExecutionContext) extends Actor {

  private[UpdateMispAlertArtifactActor] lazy val logger = Logger(getClass)
  override def preStart(): Unit = {
    eventSrv.subscribe(self, classOf[UpdateMispAlertArtifact])
    super.preStart()
  }

  override def postStop(): Unit = {
    eventSrv.unsubscribe(self)
    super.postStop()
  }

  override def receive: Receive = {
    case UpdateMispAlertArtifact() ⇒
      logger.info("UpdateMispAlertArtifact")
      userSrv
        .inInitAuthContext { implicit authContext ⇒
          mispSrv.updateMispAlertArtifact()
        }
        .onComplete {
          case Success(_)     ⇒ logger.info("Artifacts in MISP alerts updated")
          case Failure(error) ⇒ logger.error("Update MISP alert artifacts error :", error)
        }
      ()
    case msg ⇒
      logger.info(s"Receiving unexpected message: $msg (${msg.getClass})")
  }
}