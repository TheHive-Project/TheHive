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

@Singleton
class StreamMonitor @Inject() (implicit val system: ActorSystem) {
  lazy val log = Logger(getClass)
  val monitorActor = actor(new Act {
    become {
      case DeadLetter(StreamActor.GetOperations, sender, recipient) =>
        log.warn(s"receive dead GetOperations message, $sender -> $recipient")
        sender ! StreamActor.StreamNotFound
      case other =>
        log.error(s"receive dead message : $other")
    }
  })
  system.eventStream.subscribe(monitorActor, classOf[DeadLetter])
}

object StreamActor {
  case class Initialize(requestId: String) extends EventMessage
  case class Commit(requestId: String) extends EventMessage
  case object GetOperations
  case class Submit(senderRef: ActorRef)
  case class StreamMessages(messages: Seq[JsObject])
  case object StreamNotFound
}

class StreamActor(cacheExpiration: FiniteDuration,
                  refresh: FiniteDuration,
                  nextItemMaxWait: FiniteDuration,
                  globalMaxWait: FiniteDuration,
                  eventSrv: EventSrv,
                  auxSrv: AuxSrv) extends Actor with ActorLogging {
  import StreamActor._
  import context.dispatcher

  private object FakeCancellable extends Cancellable {
    def cancel() = true
    def isCancelled = true
  }

  private class WaitingRequest(senderRef: ActorRef, itemCancellable: Cancellable, globalCancellable: Cancellable, hasResult: Boolean) {
    def this(senderRef: ActorRef) = this(senderRef,
      FakeCancellable,
      context.system.scheduler.scheduleOnce(refresh, self, Submit(senderRef)),
      false)

    def renew(): WaitingRequest = {
      if (itemCancellable.cancel()) {
        if (!hasResult && globalCancellable.cancel()) {
          new WaitingRequest(senderRef,
            context.system.scheduler.scheduleOnce(nextItemMaxWait, self, Submit(senderRef)),
            context.system.scheduler.scheduleOnce(globalMaxWait, self, Submit(senderRef)),
            true)
        } else
          new WaitingRequest(senderRef,
            context.system.scheduler.scheduleOnce(nextItemMaxWait, self, Submit(senderRef)),
            globalCancellable,
            true)
      } else
        this
    }

    def submit(messages: Seq[JsObject]): Unit = {
      itemCancellable.cancel()
      globalCancellable.cancel()
      senderRef ! StreamMessages(messages)
    }
  }

  var killCancel: Cancellable = FakeCancellable

  def renewExpiration() = {
    if (killCancel.cancel())
      killCancel = context.system.scheduler.scheduleOnce(cacheExpiration, self, PoisonPill)
  }

  override def preStart() = {
    renewExpiration()
    eventSrv.subscribe(self, classOf[EventMessage])
  }
  
  override def postStop() = {
    killCancel.cancel()
    eventSrv.unsubscribe(self)
  }

  private def receiveWithState(waitingRequest: Option[WaitingRequest], currentMessages: Map[String, StreamMessageGroup[_]]): Receive = {
    case Commit(requestId) =>
      currentMessages.get(requestId).foreach { message =>
        context.become(receiveWithState(waitingRequest.map(_.renew), currentMessages + (requestId -> message.makeReady)))
      }

    case event: MigrationEvent =>
      val newMessages = currentMessages.get(event.modelName).fold(MigrationEventGroup(event)) {
        case e: MigrationEventGroup => e :+ event
      }
      context.become(receiveWithState(waitingRequest.map(_.renew), currentMessages + (event.modelName -> newMessages)))

    case EndOfMigrationEvent =>
      context.become(receiveWithState(waitingRequest.map(_.renew), currentMessages + ("end" -> MigrationEventGroup.endOfMigration)))

    case operation: AuditOperation if operation.entity.model.isInstanceOf[AuditedModel] =>
      val requestId = operation.authContext.requestId
      val updatedOperationGroup = currentMessages.get(requestId).fold(AuditOperationGroup(auxSrv, operation)) {
        case aog: AuditOperationGroup => aog :+ operation
      }
      context.become(receiveWithState(waitingRequest.map(_.renew), currentMessages + (requestId -> updatedOperationGroup)))

    case GetOperations =>
      renewExpiration()
      waitingRequest.foreach { wr =>
        wr.submit(Nil)
        log.error("Multiple requests !")
      }
      context.become(receiveWithState(Some(new WaitingRequest(sender)), currentMessages))

    case Submit(senderRef) =>
      waitingRequest match {
        case Some(wr) =>
          val (readyMessages, pendingMessages) = currentMessages.partition(_._2.isReady)
          Future.sequence(readyMessages.values.map(_.toJson)).foreach(messages => wr.submit(messages.toSeq))
          context.become(receiveWithState(None, pendingMessages))
        case None =>
          log.error("No request to submit !")
      }

    case _: Initialize             =>
    case operation: AuditOperation =>
    case message                   => log.warning(s"Unexpected message $message (${message.getClass})")
  }

  def receive = receiveWithState(None, Map.empty[String, StreamMessageGroup[_]])
}

@Singleton
class StreamFilter @Inject() (eventSrv: EventSrv,
                              implicit val mat: Materializer,
                              implicit val ec: ExecutionContext) extends Filter {

  val log = Logger(getClass)
  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    val requestId = Instance.getRequestId(requestHeader)
    eventSrv.publish(StreamActor.Initialize(requestId))
    nextFilter(requestHeader).andThen {
      case _ => eventSrv.publish(StreamActor.Commit(requestId))
    }
  }
}