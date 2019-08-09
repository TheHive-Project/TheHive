package org.thp.thehive.services.notification

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Try

import play.api.libs.json.{Format, Json}
import play.api.{Configuration, Logger}

import akka.actor.{Actor, ActorIdentity, Identify}
import akka.util.Timeout
import gremlin.scala.{__, By, Graph, Key, P, Vertex}
import javax.inject.Inject
import org.thp.scalligraph.BadConfigurationError
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EventSrv, RichElement, RichVertexGremlinScala}
import org.thp.thehive.models.{Audit, Organisation, User, UserConfig}
import org.thp.thehive.services._

object NotificationTopic {
  def apply(role: String = ""): String = if (role.isEmpty) "notification" else s"notification-$role"
}

sealed trait NotificationMessage
case class NotificationExecution(userId: String, auditId: String, notificationConfig: NotificationConfig) extends NotificationMessage

object NotificationExecution {
  implicit val format: Format[NotificationExecution] = Json.format[NotificationExecution]
}
case class AuditNotificationMessage(id: String*) extends NotificationMessage

object AuditNotificationMessage {
  implicit val format: Format[AuditNotificationMessage] = Json.format[AuditNotificationMessage]
}

class NotificationSrv @Inject()(
    userSrv: UserSrv,
    availableTriggers: immutable.Set[TriggerProvider],
    availableNotifiers: immutable.Set[NotifierProvider]
) {

  val triggers: Map[String, TriggerProvider] = availableTriggers.map(t => t.name -> t).toMap

  def getTriggers(config: String): Seq[Trigger] =
    Json
      .parse(config)
      .asOpt[Seq[NotificationConfig]]
      .fold(Seq.empty[Trigger])(_.flatMap(n => getTrigger(n.triggerConfig).toOption))

  def getTrigger(config: Configuration): Try[Trigger] =
    for {
      name            <- config.getOptional[String]("name").toRight(BadConfigurationError("name is missing")).toTry
      triggerProvider <- triggers.get(name).toRight(BadConfigurationError(s"unknown trigger $name")).toTry
      trigger         <- triggerProvider(config)
    } yield trigger

  val notifiers: Map[String, NotifierProvider] = availableNotifiers.map(n => n.name -> n).toMap

  def getNotifier(config: Configuration): Try[Notifier] =
    for {
      name             <- config.getOptional[String]("name").toRight(BadConfigurationError("name is missing")).toTry
      notifierProvider <- notifiers.get(name).toRight(BadConfigurationError(s"unknown notifier $name")).toTry
      notifier         <- notifierProvider(config)
    } yield notifier
}

class NotificationActor @Inject()(
    configuration: Configuration,
    db: Database,
    eventSrv: EventSrv,
    auditSrv: AuditSrv,
    configSrv: ConfigSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    notificationSrv: NotificationSrv
) extends Actor {
  import context.dispatcher
  lazy val logger        = Logger(getClass)
  val roles: Set[String] = configuration.get[Seq[String]]("roles").toSet

  def getTriggerMap: Map[String, Map[Trigger, (Boolean, Seq[String])]] = {
    val m = db.roTransaction(graph => configSrv.triggerMap(notificationSrv)(graph))
    logger.debug(s"Trigger map is $m") // TODO remove debug message
    m
  }

  val triggerMap: Map[String, Map[Trigger, (Boolean, Seq[String])]] = getTriggerMap

  override def preStart(): Unit = {
    roles.foreach(role => eventSrv.subscribe(NotificationTopic(role), self))
    eventSrv.subscribe(NotificationTopic(), self)
    super.preStart()
  }

  def executeForUser(userSteps: UserSteps, audit: Audit with Entity, context: Entity, organisation: Organisation with Entity)(
      implicit db: Database,
      graph: Graph
  ): Unit = { // FIXME doesn't work for organisation based notification
    userSteps
      .project(
        _.and(By[Vertex]())
          .apply(By(__[Vertex].outTo[UserConfig].has(Key[String]("name"), P.eq[String]("notification")).value[String]("value")))
      )
      .map {
        case (userVertex, notificationConfig) =>
          Json
            .parse(notificationConfig)
            .asOpt[Seq[NotificationConfig]]
            .getOrElse(Nil)
            .foreach {
              case notificationConfig if notificationConfig.roleRestriction.isEmpty || (notificationConfig.roleRestriction & roles).nonEmpty =>
                val user = userVertex.as[User]
                for {
                  trigger <- notificationSrv.getTrigger(notificationConfig.triggerConfig)
                  if trigger.filter(audit, context, organisation, user)
                  notifier <- notificationSrv.getNotifier(notificationConfig.notifierConfig)
                  _ = logger.debug(s"Execution of notifier ${notifier.name} for user $user")
                } yield notifier.execute(audit, context, organisation, user)
              case notificationConfig =>
                logger.debug(s"Notification has role restriction($notificationConfig.roleRestriction) and it is not applicable here ($roles)")
                Future
                  .firstCompletedOf(notificationConfig.roleRestriction.map { role =>
                    eventSrv.publishAsk(NotificationTopic(role))(Identify(1))(Timeout(2.seconds))
                  })
                  .map {
                    case ActorIdentity(1, Some(notificationActor)) =>
                      logger.debug(s"Send notification to $notificationActor")
                      notificationActor ! NotificationExecution(userVertex.id().toString, audit._id, notificationConfig)
                  }
            }
      }
      .iterate()
    ()
  }

  override def receive: Receive = {
    case AuditNotificationMessage(ids @ _*) =>
      logger.debug(s"Receive AuditStreamMessage(${ids.mkString(",")})")
      db.roTransaction { implicit graph =>
        auditSrv
          .getByIds(ids: _*)
          .auditContextOrganisation
          .toIterator
          .foreach {
            case (audit, context, organisation) =>
              logger.debug(s"Notification is related to $audit, $context, $organisation")
              triggerMap
                .getOrElse(organisation._id, Map.empty)
                .foreach {
                  case (trigger, (inOrg, userIds)) if trigger.preFilter(audit, context, organisation) =>
                    logger.debug(s"Notification trigger ${trigger.name} is applicable")
                    executeForUser(userSrv.getByIds(userIds: _*), audit, context, organisation)(db, graph)
                    if (inOrg) {
                      val users = organisationSrv.get(organisation).users.where(_.config.hasNot(Key[String]("name"), P.eq("notification")))
                      executeForUser(users, audit, context, organisation)(db, graph)
                    }
                  case (trigger, _) => logger.debug(s"Notification trigger ${trigger.name} is NOT applicable")
                }
          }
      }
    case NotificationExecution(userId, auditId, notificationConfig) =>
      db.roTransaction { implicit graph =>
        auditSrv.getByIds(auditId).auditContextOrganisation.getOrFail().foreach {
          case (audit, context, organisation) =>
            for {
              user    <- userSrv.getOrFail(userId)
              trigger <- notificationSrv.getTrigger(notificationConfig.triggerConfig)
              if trigger.filter(audit, context, organisation, user)
              notifier <- notificationSrv.getNotifier(notificationConfig.notifierConfig)
              _ = logger.debug(s"Execution of notifier ${notifier.name} for user $user")
            } yield notifier.execute(audit, context, organisation, user)
        }
      }
  }
}
