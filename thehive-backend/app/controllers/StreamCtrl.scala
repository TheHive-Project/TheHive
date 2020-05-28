package controllers

import akka.actor.{ActorIdentity, ActorSystem, Identify, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Put, Send}
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import javax.inject.{Inject, Singleton}
import models.Roles
import org.elastic4play.Timed
import org.elastic4play.controllers._
import org.elastic4play.services.{MigrationSrv, UserSrv}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc._
import play.api.{Configuration, Logger}
import services.StreamActor
import services.StreamActor.StreamMessages

import scala.collection.immutable
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

@Singleton
class StreamCtrl(
    cacheExpiration: FiniteDuration,
    refresh: FiniteDuration,
    authenticated: Authenticated,
    renderer: Renderer,
    userSrv: UserSrv,
    migrationSrv: MigrationSrv,
    components: ControllerComponents,
    implicit val system: ActorSystem,
    implicit val ec: ExecutionContext
) extends AbstractController(components)
    with Status {

  @Inject() def this(
      configuration: Configuration,
      authenticated: Authenticated,
      renderer: Renderer,
      userSrv: UserSrv,
      migrationSrv: MigrationSrv,
      components: ControllerComponents,
      system: ActorSystem,
      ec: ExecutionContext
  ) =
    this(
      configuration.getMillis("stream.longpolling.cache").millis,
      configuration.getMillis("stream.longpolling.refresh").millis,
      authenticated,
      renderer,
      userSrv,
      migrationSrv,
      components,
      system,
      ec
    )

  private val streamLength                               = 20
  private lazy val logger                                = Logger(getClass)
  private val mediator                                   = DistributedPubSub(system).mediator
  private val alphanumeric: immutable.IndexedSeq[Char]   = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
  private def generateStreamId()                         = Seq.fill(streamLength)(alphanumeric(Random.nextInt(alphanumeric.size))).mkString
  private def isValidStreamId(streamId: String): Boolean = streamId.length == streamLength && streamId.forall(alphanumeric.contains)

  /**
    * Create a new stream entry with the event head
    */
  @Timed("controllers.StreamCtrl.create")
  def create: Action[AnyContent] = authenticated(Roles.read) {
    val id          = generateStreamId()
    val streamActor = system.actorOf(Props(classOf[StreamActor], cacheExpiration, refresh), s"stream-$id")
    logger.debug(s"Register stream actor $streamActor")
    mediator ! Put(streamActor)
    Ok(id)
  }

  /**
    * Get events linked to the identified stream entry
    * This call waits up to "refresh", if there is no event, return empty response
    */
  @Timed("controllers.StreamCtrl.get")
  def get(id: String): Action[AnyContent] = Action.async { implicit request ⇒
    implicit val timeout: Timeout = Timeout(refresh + 1.second)

    if (!isValidStreamId(id)) {
      Future.successful(BadRequest("Invalid stream id"))
    } else {
      val futureStatus = authenticated.expirationStatus(request) match {
        case ExpirationError if !migrationSrv.isMigrating ⇒
          userSrv.getInitialUser(request).recoverWith { case _ ⇒ authenticated.getFromApiKey(request) }.map(_ ⇒ OK)
        case _: ExpirationWarning ⇒ Future.successful(220)
        case _                    ⇒ Future.successful(OK)
      }

      // Check if stream actor exists
      mediator
        .ask(Send(s"/user/stream-$id", Identify(1), localAffinity = false))(Timeout(2.seconds))
        .flatMap {
          case ActorIdentity(1, Some(_)) ⇒
            futureStatus.flatMap { status ⇒
              (mediator ? Send(s"/user/stream-$id", StreamActor.GetOperations, localAffinity = false)) map {
                case StreamMessages(operations) ⇒ renderer.toOutput(status, operations)
                case m                          ⇒ InternalServerError(s"Unexpected message : $m (${m.getClass})")
              }
            }
          case _ ⇒ Future.successful(renderer.toOutput(NOT_FOUND, Json.obj("type" → "StreamNotFound", "message" → s"Stream $id doesn't exist")))
        }
        .recover {
          case _: AskTimeoutException ⇒ renderer.toOutput(NOT_FOUND, Json.obj("type" → "StreamNotFound", "message" → s"Stream $id doesn't exist"))
        }
    }
  }

  @Timed("controllers.StreamCtrl.status")
  def status: Action[AnyContent] = Action { implicit request ⇒
    val status = authenticated.expirationStatus(request) match {
      case ExpirationWarning(duration) ⇒ Json.obj("remaining" → duration.toSeconds, "warning" → true)
      case ExpirationError             ⇒ Json.obj("remaining" → 0, "warning"                  → true)
      case ExpirationOk(duration)      ⇒ Json.obj("remaining" → duration.toSeconds, "warning" → false)
    }
    Ok(status)
  }
}
