package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

import akka.actor.{ ActorLogging, ActorRef, ActorSystem, Cancellable, DeadLetter, PoisonPill, actorRef2Scala }
import akka.actor.Actor
import akka.actor.ActorDSL.{ Act, actor }
import akka.stream.Materializer

import play.api.Logger
import play.api.libs.json.JsObject
import play.api.mvc.{ Filter, RequestHeader, Result }

import org.elastic4play.services.{ AuditOperation, AuxSrv, EndOfMigrationEvent, EventMessage, EventSrv, MigrationEvent }
import org.elastic4play.utils.Instance

/**
 * This actor monitors dead messages and log them
 */
@Singleton
class StreamMonitor @Inject() (implicit val system: ActorSystem) {
  lazy val logger = Logger(getClass)
  val monitorActor: ActorRef = actor(new Act {
    become {
      case DeadLetter(StreamActor.GetOperations, sender, recipient) ⇒
        logger.warn(s"receive dead GetOperations message, $sender -> $recipient")
        sender ! StreamActor.StreamNotFound
      case other ⇒
        logger.error(s"receive dead message : $other")
    }
  })
  system.eventStream.subscribe(monitorActor, classOf[DeadLetter])
}

object StreamActor {
  /* Start of a new request identified by its id */
  case class Initialize(requestId: String) extends EventMessage
  /* Request process has finished, prepare to send associated messages */
  case class Commit(requestId: String) extends EventMessage
  /* Ask messages, wait if there is no ready messages*/
  case object GetOperations
  /* Pending messages must be sent to sender */
  case object Submit
  /* List of ready messages */
  case class StreamMessages(messages: Seq[JsObject])
  case object StreamNotFound
}

class StreamActor(
    cacheExpiration: FiniteDuration,
    refresh: FiniteDuration,
    nextItemMaxWait: FiniteDuration,
    globalMaxWait: FiniteDuration,
    eventSrv: EventSrv,
    auxSrv: AuxSrv) extends Actor with ActorLogging {
  import services.StreamActor._
  import context.dispatcher

  lazy val logger = Logger(getClass)

  private object FakeCancellable extends Cancellable {
    def cancel() = true
    def isCancelled = true
  }

  private class WaitingRequest(senderRef: ActorRef, itemCancellable: Cancellable, globalCancellable: Cancellable, hasResult: Boolean) {
    def this(senderRef: ActorRef) = this(
      senderRef,
      FakeCancellable,
      context.system.scheduler.scheduleOnce(refresh, self, Submit),
      false)

    /**
     * Renew timers
     */
    def renew: WaitingRequest = {
      if (itemCancellable.cancel()) {
        if (!hasResult && globalCancellable.cancel()) {
          new WaitingRequest(
            senderRef,
            context.system.scheduler.scheduleOnce(nextItemMaxWait, self, Submit),
            context.system.scheduler.scheduleOnce(globalMaxWait, self, Submit),
            true)
        }
        else
          new WaitingRequest(
            senderRef,
            context.system.scheduler.scheduleOnce(nextItemMaxWait, self, Submit),
            globalCancellable,
            true)
      }
      else
        this
    }

    /**
     * Send message
     */
    def submit(messages: Seq[JsObject]): Unit = {
      itemCancellable.cancel()
      globalCancellable.cancel()
      senderRef ! StreamMessages(messages)
    }
  }

  var killCancel: Cancellable = FakeCancellable

  /**
   * renew global timer and rearm it
   */
  def renewExpiration(): Unit = {
    if (killCancel.cancel())
      killCancel = context.system.scheduler.scheduleOnce(cacheExpiration, self, PoisonPill)
  }

  override def preStart(): Unit = {
    renewExpiration()
    eventSrv.subscribe(self, classOf[EventMessage])
  }

  override def postStop(): Unit = {
    killCancel.cancel()
    eventSrv.unsubscribe(self)
  }

  private def normalizeOperation(operation: AuditOperation) = {
    operation.entity.model match {
      case am: AuditedModel ⇒ operation.copy(details = am.selectAuditedAttributes(operation.details))
    }
  }
  private def receiveWithState(waitingRequest: Option[WaitingRequest], currentMessages: Map[String, Option[StreamMessageGroup[_]]]): Receive = {
    /* End of HTTP request, mark received messages to ready*/
    case Commit(requestId) ⇒
      currentMessages.get(requestId).foreach {
        case Some(message) ⇒
          context.become(receiveWithState(waitingRequest.map(_.renew), currentMessages + (requestId → Some(message.makeReady))))
        case None ⇒
      }

    /* Migration process event */
    case event: MigrationEvent ⇒
      val newMessages = currentMessages.get(event.modelName).flatten.fold(MigrationEventGroup(event)) {
        case e: MigrationEventGroup ⇒ e :+ event
      }
      context.become(receiveWithState(waitingRequest.map(_.renew), currentMessages + (event.modelName → Some(newMessages))))

    /* Database migration has just finished */
    case EndOfMigrationEvent ⇒
      context.become(receiveWithState(waitingRequest.map(_.renew), currentMessages + ("end" → Some(MigrationEventGroup.endOfMigration))))

    /* */
    case operation: AuditOperation if operation.entity.model.isInstanceOf[AuditedModel] ⇒
      val requestId = operation.authContext.requestId
      val normalizedOperation = normalizeOperation(operation)
      logger.debug(s"Receiving audit operation : $operation => $normalizedOperation")
      val updatedOperationGroup = currentMessages.get(requestId) match {
        case None ⇒
          logger.debug("Operation that comes after the end of request, make operation ready to send")
          AuditOperationGroup(auxSrv, normalizedOperation).makeReady // Operation that comes after the end of request
        case Some(None) ⇒
          logger.debug("First operation of the request, creating operation group")
          AuditOperationGroup(auxSrv, normalizedOperation) // First operation related to the given request
        case Some(Some(aog: AuditOperationGroup)) ⇒
          logger.debug("Operation included in existing group")
          aog :+ normalizedOperation
        case _ ⇒
          logger.debug("Impossible")
          sys.error("")
      }
      context.become(receiveWithState(waitingRequest.map(_.renew), currentMessages + (requestId → Some(updatedOperationGroup))))

    case GetOperations ⇒
      renewExpiration()
      waitingRequest.foreach { wr ⇒
        wr.submit(Nil)
        logger.error("Multiple requests !")
      }
      context.become(receiveWithState(Some(new WaitingRequest(sender)), currentMessages))

    case Submit ⇒
      waitingRequest match {
        case Some(wr) ⇒
          val (readyMessages, pendingMessages) = currentMessages.partition(_._2.fold(false)(_.isReady))
          Future.sequence(readyMessages.values.map(_.get.toJson)).foreach(messages ⇒ wr.submit(messages.toSeq))
          context.become(receiveWithState(None, pendingMessages))
        case None ⇒
          logger.error("No request to submit !")
      }

    case Initialize(requestId) ⇒ context.become(receiveWithState(waitingRequest, currentMessages + (requestId → None)))
    case _: AuditOperation     ⇒
    case message               ⇒ logger.warn(s"Unexpected message $message (${message.getClass})")
  }

  def receive: Receive = receiveWithState(None, Map.empty[String, Option[StreamMessageGroup[_]]])
}

@Singleton
class StreamFilter @Inject() (
    eventSrv: EventSrv,
    implicit val mat: Materializer,
    implicit val ec: ExecutionContext) extends Filter {

  val log = Logger(getClass)
  def apply(nextFilter: RequestHeader ⇒ Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    val requestId = Instance.getRequestId(requestHeader)
    eventSrv.publish(StreamActor.Initialize(requestId))
    nextFilter(requestHeader).andThen {
      case _ ⇒ eventSrv.publish(StreamActor.Commit(requestId))
    }
  }
}