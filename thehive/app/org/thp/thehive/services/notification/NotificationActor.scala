package org.thp.thehive.services.notification

import akka.actor.{Actor, ActorIdentity, Identify}
import akka.util.Timeout
import org.thp.scalligraph.models.{Database, Entity, Schema}
import org.thp.scalligraph.services.EventSrv
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{BadConfigurationError, EntityId}
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services.AuditOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import org.thp.thehive.services.notification.notifiers.{Notifier, NotifierProvider}
import org.thp.thehive.services.notification.triggers.{Trigger, TriggerProvider}
import play.api.cache.SyncCacheApi
import play.api.libs.json.{Format, JsValue, Json}
import play.api.{Configuration, Logger}

import javax.inject.Inject
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Success, Try}

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

class NotificationSrv @Inject() (
    availableTriggers: immutable.Set[TriggerProvider],
    availableNotifiers: immutable.Set[NotifierProvider]
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

class NotificationActor @Inject() (
    configuration: Configuration,
    eventSrv: EventSrv,
    auditSrv: AuditSrv,
    configSrv: ConfigSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    notificationSrv: NotificationSrv,
    cache: SyncCacheApi,
    implicit val db: Database,
    implicit val schema: Schema
) extends Actor {
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
      context: Option[Map[String, Seq[Any]] with Entity],
      `object`: Option[Map[String, Seq[Any]] with Entity],
      organisation: Organisation with Entity
  )(implicit
      graph: Graph
  ): Unit =
    notificationConfigs
      .foreach {
        case notificationConfig if notificationConfig.roleRestriction.isEmpty || (notificationConfig.roleRestriction & roles).nonEmpty =>
          notificationSrv
            .getTrigger(notificationConfig.triggerConfig)
            .flatMap { trigger =>
              logger.debug(s"Checking trigger $trigger against $audit, $context, $organisation, $user")
              if (trigger.filter(audit, context, organisation, user)) notificationSrv.getNotifier(notificationConfig.notifierConfig).map(Some(_))
              else Success(None)
            }
            .map(_.foreach { notififer =>
              logger.info(s"Execution of notifier $notififer for user $user")
              notififer.execute(audit, context, `object`, organisation, user).failed.foreach { error =>
                logger.error(s"Execution of notifier $notififer has failed for user $user", error)
              }
            })
            .failed
            .foreach { error =>
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
            case (audit, context, obj, organisations) =>
              logger.debug(s"Notification is related to $audit, $context, ${organisations.map(_.name).mkString(",")}")
              organisations.foreach { organisation =>
                lazy val organisationNotificationConfigs = organisationSrv
                  .get(organisation)
                  .config
                  .has(_.name, "notification")
                  .value(_.value)
                  .headOption
                  .toSeq
                  .flatMap(_.asOpt[Seq[NotificationConfig]].getOrElse(Nil))
                val orgNotifs = triggerMap
                  .getOrElse(organisation._id, Map.empty)
                val mustNotifyOrganisation = orgNotifs
                  .exists {
                    case (trigger, (true, _)) => trigger.preFilter(audit, context, organisation)
                    case _                    => false
                  }
                if (mustNotifyOrganisation)
                  executeNotification(None, organisationNotificationConfigs.filterNot(_.delegate), audit, context, obj, organisation)
                val mustNotifyOrgUsers = orgNotifs.exists {
                  case (trigger, (false, _)) => trigger.preFilter(audit, context, organisation)
                  case _                     => false
                }
                if (mustNotifyOrgUsers) {
                  val userConfig = organisationNotificationConfigs.filter(_.delegate)
                  organisationSrv
                    .get(organisation)
                    .users
                    .filter(_.config.hasNot(_.name, "notification"))
                    .toIterator
                    .foreach { user =>
                      executeNotification(Some(user), userConfig, audit, context, obj, organisation)
                    }
                }
                val usersToNotify = orgNotifs.flatMap {
                  case (trigger, (_, userIds)) if userIds.nonEmpty && trigger.preFilter(audit, context, organisation) => userIds
                  case _                                                                                              => Nil
                }.toSeq
                userSrv
                  .getByIds(usersToNotify: _*)
                  .project(_.by.by(_.config.has(_.name, "notification").value(_.value).option))
                  .foreach {
                    case (user, Some(config)) =>
                      config.asOpt[Seq[NotificationConfig]].foreach { userConfig =>
                        executeNotification(Some(user), userConfig, audit, context, obj, organisation)
                      }
                    case _ =>
                  }
              }
            case _ =>
          }
      }
    case NotificationExecution(userId, auditId, notificationConfig) =>
      db.roTransaction { implicit graph =>
        auditSrv.getByIds(auditId).auditContextObjectOrganisation.getOrFail("Audit").foreach {
          case (audit, context, obj, organisations) =>
            organisations.foreach { organisation =>
              for {
                user    <- userId.map(userSrv.getOrFail).flip
                trigger <- notificationSrv.getTrigger(notificationConfig.triggerConfig)
                if trigger.filter(audit, context, organisation, user)
                notifier <- notificationSrv.getNotifier(notificationConfig.notifierConfig)
                _ = logger.debug(s"Execution of notifier ${notifier.name} for user $user")
              } yield notifier.execute(audit, context, obj, organisation, user)
            }
          case _ => // TODO
        }
      }
  }
}
