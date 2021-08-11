package org.thp.thehive.services.notification

import akka.actor.{Actor, ActorIdentity, Identify}
import akka.util.Timeout
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.EventSrv
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.{BadConfigurationError, EntityId}
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services._
import org.thp.thehive.services.notification.notifiers.{Notifier, NotifierProvider}
import org.thp.thehive.services.notification.triggers.{Trigger, TriggerProvider}
import play.api.cache.SyncCacheApi
import play.api.libs.json.{Format, JsValue, Json}
import play.api.{Configuration, Logger}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Try

object NotificationTopic {
  def apply(role: String = ""): String = if (role.isEmpty) "notification" else s"notification-$role"
}

sealed trait NotificationMessage
case class NotificationExecution(userId: Option[EntityId], auditId: EntityId, notificationConfig: NotificationConfig) extends NotificationMessage

object NotificationExecution {
  implicit val format: Format[NotificationExecution] = Json.format[NotificationExecution]
}
case class AuditNotificationMessage(id: EntityId*) extends NotificationMessage

object AuditNotificationMessage {
  implicit val format: Format[AuditNotificationMessage] = Json.format[AuditNotificationMessage]
}

class NotificationSrv(
    availableTriggers: Seq[TriggerProvider],
    availableNotifiers: Seq[NotifierProvider]
) {

  val triggers: Map[String, TriggerProvider] = availableTriggers.map(t => t.name -> t).toMap

  def getConfig(config: String): Seq[NotificationConfig] =
    Json
      .parse(config)
      .asOpt[Seq[NotificationConfig]]
      .getOrElse(Nil)

  def getTriggers(config: JsValue): Seq[Trigger] =
    config.asOpt[Seq[NotificationConfig]].getOrElse(Nil).flatMap(c => getTrigger(c.triggerConfig).toOption)

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

sealed trait NotificationTag
class NotificationActor(
    configuration: Configuration,
    eventSrv: EventSrv,
    auditSrv: AuditSrv,
    configSrv: ConfigSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    notificationSrv: NotificationSrv,
    cache: SyncCacheApi,
    db: Database
) extends Actor
    with TheHiveOpsNoDeps {
  import context.dispatcher
  lazy val logger: Logger = Logger(getClass)
  val roles: Set[String]  = configuration.get[Seq[String]]("roles").toSet

  // Map of OrganisationId -> Trigger -> (present in org, list of UserId) */
  def triggerMap: Map[EntityId, Map[Trigger, (Boolean, Seq[EntityId])]] =
    cache.getOrElseUpdate("notification-triggers", 5.minutes)(db.roTransaction(graph => configSrv.triggerMap(notificationSrv)(graph)))

  override def preStart(): Unit = {
    roles.foreach(role => eventSrv.subscribe(NotificationTopic(role), self))
    eventSrv.subscribe(NotificationTopic(), self)
    super.preStart()
  }

  /**
    * Execute the notification for that user if the trigger is applicable. If the role restriction configured in the
    * notification configuration, send it to an actor which can execute this notification. The role restriction is
    * related to the host, not to the user. When TheHive is executed in a cluster, a role can be assigned to each host.
    * @param user the targeted user
    * @param notificationConfigs the notification config. Contains the trigger, the role restriction and the notifier
    * @param audit message to be notified
    * @param context context element of the audit
    * @param organisation organisation of the user
    * @param graph the graph
    */
  def executeNotification(
      user: Option[User with Entity],
      notificationConfigs: Seq[NotificationConfig],
      audit: Audit with Entity,
      context: Option[Entity],
      `object`: Option[Entity],
      organisation: Organisation with Entity
  )(implicit
      graph: Graph
  ): Unit =
    notificationConfigs
      .foreach {
        case notificationConfig if notificationConfig.roleRestriction.isEmpty || (notificationConfig.roleRestriction & roles).nonEmpty =>
          val result = for {
            trigger <- notificationSrv.getTrigger(notificationConfig.triggerConfig)
            if trigger.filter(audit, context, organisation, user)
            notifier <- notificationSrv.getNotifier(notificationConfig.notifierConfig)
            _ = logger.info(s"Execution of notifier ${notifier.name} for user $user")
          } yield notifier.execute(audit, context, `object`, organisation, user).failed.foreach { error =>
            logger.error(s"Execution of notifier ${notifier.name} has failed for user $user", error)
          }
          result.failed.foreach { error =>
            logger.error(s"Execution of notification $notificationConfig has failed for user $user / ${organisation.name}", error)
          }
        case notificationConfig =>
          logger.debug(s"Notification has role restriction($notificationConfig.roleRestriction) and it is not applicable here ($roles)")
          Future
            .firstCompletedOf(notificationConfig.roleRestriction.map { role =>
              eventSrv.publishAsk(NotificationTopic(role))(Identify(1))(Timeout(2.seconds))
            })
            .map {
              case ActorIdentity(1, Some(notificationActor)) =>
                logger.debug(s"Send notification to $notificationActor")
                notificationActor ! NotificationExecution(user.map(_._id), audit._id, notificationConfig)
              case other => logger.error(s"Unexpected message (found: ${other.getClass}, expected: ActorIdentity)")
            }
      }

  override def receive: Receive = {
    case AuditNotificationMessage(ids @ _*) =>
      logger.debug(s"Receive AuditStreamMessage(${ids.mkString(",")})")
      db.roTransaction { implicit graph =>
        auditSrv
          .getByIds(ids: _*)
          .auditContextObjectOrganisation
          .toIterator
          .foreach {
            case (audit, context, obj, Some(organisation)) =>
              logger.debug(s"Notification is related to $audit, $context, $organisation")
              triggerMap
                .getOrElse(organisation._id, Map.empty)
                .foreach {
                  case (trigger, (inOrg, userIds)) if trigger.preFilter(audit, context, organisation) =>
                    logger.debug(s"Notification trigger ${trigger.name} is applicable for $audit")
                    if (userIds.nonEmpty)
                      userSrv
                        .getByIds(userIds: _*)
                        .project(
                          _.by
                            .by(_.config("notification").value(_.value).fold)
                        )
                        .toIterator
                        .foreach {
                          case (user, notificationConfig) =>
                            val config = notificationConfig.flatMap(_.asOpt[NotificationConfig])
                            executeNotification(Some(user), config, audit, context, obj, organisation)
                        }
                    if (inOrg)
                      organisationSrv
                        .get(organisation)
                        .config
                        .has(_.name, "notification")
                        .value(_.value)
                        .toIterator
                        .foreach { notificationConfig: JsValue =>
                          val (userConfig, orgConfig) = notificationConfig
                            .asOpt[Seq[NotificationConfig]]
                            .getOrElse(Nil)
                            .partition(_.delegate)
                          organisationSrv
                            .get(organisation)
                            .users
                            .not(_.config.has(_.name, "notification"))
                            .toIterator
                            .foreach { user =>
                              executeNotification(Some(user), userConfig, audit, context, obj, organisation)
                            }
                          executeNotification(None, orgConfig, audit, context, obj, organisation)
                        }
                  case (trigger, _) => logger.debug(s"Notification trigger ${trigger.name} is NOT applicable for $audit")
                }
            case _ =>
          }
      }
    case NotificationExecution(userId, auditId, notificationConfig) =>
      db.roTransaction { implicit graph =>
        auditSrv.getByIds(auditId).auditContextObjectOrganisation.getOrFail("Audit").foreach {
          case (audit, context, obj, Some(organisation)) =>
            for {
              user    <- userId.map(userSrv.getOrFail).flip
              trigger <- notificationSrv.getTrigger(notificationConfig.triggerConfig)
              if trigger.filter(audit, context, organisation, user)
              notifier <- notificationSrv.getNotifier(notificationConfig.notifierConfig)
              _ = logger.debug(s"Execution of notifier ${notifier.name} for user $user")
            } yield notifier.execute(audit, context, obj, organisation, user)
          case _ => // TODO
        }
      }
  }
}
