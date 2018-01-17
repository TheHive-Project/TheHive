package services

import javax.inject.Inject

import scala.concurrent.Future
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

import play.api.Logger
import play.api.libs.json.JsObject

import akka.actor.{ Actor, ActorRef, Cancellable, PoisonPill, actorRef2Scala }
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{ Publish, Subscribe, Unsubscribe }
import services.StreamActor.{ StreamMessages, Submit }

import org.elastic4play.services._
import org.elastic4play.utils.Instance

trait StreamActorMessage extends Serializable
object StreamActor {
  /* Ask messages, wait if there is no ready messages */
  case object GetOperations extends StreamActorMessage

  /* Pending messages must be sent to sender */
  case object Submit extends StreamActorMessage

  /* List of ready messages */
  case class StreamMessages(messages: Seq[JsObject]) extends StreamActorMessage

  object StreamMessages {
    val empty = StreamMessages(Nil)
  }
}

/**
  * This actor receive message generated locally and when aggregation is finished (http request is over) send the message
  * to global stream actor.
  */
class LocalStreamActor @Inject() (
    eventSrv: EventSrv,
    auxSrv: AuxSrv) extends Actor {

  import context.dispatcher

  private lazy val logger = Logger(s"${getClass.getName}($self)")
  private val mediator = DistributedPubSub(context.system).mediator

  override def preStart(): Unit = {
    eventSrv.subscribe(self, classOf[EventMessage])
    super.preStart()
  }

  object NormalizedOperation {
    def unapply(msg: Any): Option[AuditOperation] =
      msg match {
        case ao: AuditOperation ⇒ ao.entity.model match {
          case am: AuditedModel ⇒ Some(ao.copy(details = am.selectAuditedAttributes(ao.details)))
          case _                ⇒ None
        }
        case _ ⇒ None
      }
  }

  object RequestStart {
    def unapply(msg: Any): Option[String] = msg match {
      case RequestProcessStart(request)           ⇒ Some(Instance.getRequestId(request))
      case InternalRequestProcessStart(requestId) ⇒ Some(requestId)
      case _                                      ⇒ None
    }
  }

  object RequestEnd {
    def unapply(msg: Any): Option[String] = msg match {
      case RequestProcessEnd(request, _)        ⇒ Some(Instance.getRequestId(request))
      case InternalRequestProcessEnd(requestId) ⇒ Some(requestId)
      case _                                    ⇒ None
    }
  }

  override def receive: Receive = receive(Map.empty, None)

  def receive(messages: Map[String, Option[AggregatedMessage[_]]], flushScheduler: Option[Cancellable]): Receive = {
    case RequestStart(requestId) ⇒
      context.become(receive(messages + (requestId -> None), None))

    case RequestEnd(requestId) ⇒
      messages.get(requestId).collect {
        case Some(message) ⇒ message.toJson.foreach(msg ⇒ mediator ! Publish("stream", StreamMessages(Seq(msg))))
      }
      context.become(receive(messages - requestId, None))

    case NormalizedOperation(operation) ⇒
      val requestId = operation.authContext.requestId
      logger.debug(s"Receiving audit operation : $operation")
      messages.get(requestId) match {
        case None ⇒
          logger.debug("Operation that comes after the end of request, send it to stream actor")
          AggregatedAuditMessage(auxSrv, operation).toJson.map(msg ⇒ mediator ! Publish("stream", msg))
        case Some(None) ⇒
          logger.debug("First operation of the request, creating operation group")
          context.become(receive(messages + (requestId -> Some(AggregatedAuditMessage(auxSrv, operation))), None))
        case Some(Some(aam: AggregatedAuditMessage)) ⇒
          logger.debug("Operation included in existing group")
          context.become(receive(messages + (requestId -> Some(aam.add(operation))), None))
        case _ ⇒
          logger.debug("Impossible")
          sys.error("")
      }

    /* Migration process event */
    case event: MigrationEvent ⇒
      val newMessage = messages.get(event.modelName).flatten match {
        case Some(m: AggregatedMigrationMessage) ⇒ m.add(event)
        case None                                ⇒ AggregatedMigrationMessage(event)
        case _                                   ⇒ sys.error("impossible")
      }
      // automatically flush messages after 1s
      val newFlushScheduler = flushScheduler.getOrElse(context.system.scheduler.scheduleOnce(1.second, self, Submit))
      context.become(receive(messages + (event.modelName → Some(newMessage)), Some(newFlushScheduler)))

    /* Database migration has just finished */
    case EndOfMigrationEvent ⇒
      flushScheduler.foreach(_.cancel())
      self ! Submit
      context.become(receive(messages + ("end" → Some(AggregatedMigrationMessage.endOfMigration)), None))

    case Submit ⇒
      Future
        .traverse(messages.values.flatten)(_.toJson)
        .foreach(message ⇒ mediator ! Publish("stream", StreamMessages(message.toSeq)))
      context.become(receive(Map.empty, None))
  }
}

class StreamActor(
    cacheExpiration: FiniteDuration,
    refresh: FiniteDuration) extends Actor {

  import context.dispatcher
  import services.StreamActor._

  private lazy val logger = Logger(s"${getClass.getName}($self)")
  private var killCancel: Cancellable = context.system.scheduler.scheduleOnce(cacheExpiration, self, PoisonPill)
  private val mediator = DistributedPubSub(context.system).mediator

  /**
    * renew global timer and rearm it
    */
  def renewExpiration(): Unit = {
    killCancel.cancel()
    killCancel = context.system.scheduler.scheduleOnce(cacheExpiration, self, PoisonPill)
  }

  override def preStart(): Unit = {
    renewExpiration()
    mediator ! Subscribe("stream", self)
    super.preStart()
  }

  override def postStop(): Unit = {
    killCancel.cancel()
    mediator ! Unsubscribe("stream", self)
    super.postStop()
  }

  private def receive(waitingRequest: ActorRef): Receive = {
    logger.debug(s"Waiting messages to send to $waitingRequest")
    renewExpiration()
    val timeout = context.system.scheduler.scheduleOnce(refresh, self, Submit)

    {
      case sm: StreamMessages ⇒
        waitingRequest ! sm
        timeout.cancel()
        context.become(receive)
      case Submit ⇒
        waitingRequest ! StreamMessages.empty
        timeout.cancel()
        context.become(receive)
      case GetOperations ⇒
        waitingRequest ! StreamMessages.empty
        timeout.cancel()
        context.become(receive(sender))
    }
  }

  private def receive(waitingMessages: Seq[JsObject]): Receive = {
    case GetOperations ⇒
      sender ! StreamMessages(waitingMessages)
      renewExpiration()
      context.become(receive)
    case StreamMessages(msg) ⇒
      context.become(receive(waitingMessages ++ msg))
  }

  def receive: Receive = {
    case StreamMessages(msg) ⇒ context.become(receive(msg))
    case GetOperations       ⇒ context.become(receive(sender))
  }
}