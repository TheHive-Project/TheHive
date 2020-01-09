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
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.models.{Audit, Organisation, User, UserConfig}
import org.thp.thehive.services._
import org.thp.thehive.services.notification.notifiers.{Notifier, NotifierProvider}
import org.thp.thehive.services.notification.triggers.{Trigger, TriggerProvider}

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
    eventSrv: EventSrv,
    auditSrv: AuditSrv,
    configSrv: ConfigSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    notificationSrv: NotificationSrv,
    implicit val db: Database
) extends Actor {
  import context.dispatcher
  lazy val logger: Logger = Logger(getClass)
  val roles: Set[String]  = configuration.get[Seq[String]]("roles").toSet

  // TODO this map must be updated (or cached)
  /* Map of OrganisationId -> Trigger -> (applied for that org ?, list of UserId) */
  def getTriggerMap: Map[String, Map[Trigger, (Boolean, Seq[String])]] = db.roTransaction(graph => configSrv.triggerMap(notificationSrv)(graph))

  val triggerMap: Map[String, Map[Trigger, (Boolean, Seq[String])]] = getTriggerMap

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
    * @param notificationConfig the notification config. Contains the trigger, the role restriction and the notifier
    * @param audit message to be notified
    * @param context context element of the audit
    * @param organisation organisation of the user
    * @param graph the graph
    */
  def executeForUser(
      user: User with Entity,
      notificationConfig: Seq[NotificationConfig],
      audit: Audit with Entity,
      context: Option[Entity],
      `object`: Option[Entity],
      organisation: Organisation with Entity
  )(
      implicit graph: Graph
  ): Unit =
    notificationConfig
      .foreach {
        case notificationConfig if notificationConfig.roleRestriction.isEmpty || (notificationConfig.roleRestriction & roles).nonEmpty =>
          for {
            trigger <- notificationSrv.getTrigger(notificationConfig.triggerConfig)
            if trigger.filter(audit, context, organisation, user)
            notifier <- notificationSrv.getNotifier(notificationConfig.notifierConfig)
            _ = logger.info(s"Execution of notifier ${notifier.name} for user $user")
          } yield notifier.execute(audit, context, `object`, organisation, user).failed.foreach { error =>
            logger.error(s"Execution of notifier ${notifier.name} has failed for user $user", error)
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
                notificationActor ! NotificationExecution(user._id, audit._id, notificationConfig)
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
                    logger.debug(s"Notification trigger ${trigger.name} is applicable")
                    userSrv
                      .getByIds(userIds: _*)
                      .project(
                        _.and(By[Vertex]())
                          .apply(By(__[Vertex].outTo[UserConfig].has(Key[String]("name"), P.eq[String]("notification")).value[String]("value")))
                      )
                      .toIterator
                      .foreach {
                        case (userVertex, notificationConfig) =>
                          val user = userVertex.as[User]
                          val config = Json
                            .parse(notificationConfig)
                            .asOpt[Seq[NotificationConfig]]
                            .getOrElse(Nil)
                          executeForUser(user, config, audit, context, obj, organisation)
                      }
                    if (inOrg) {
                      organisationSrv
                        .get(organisation)
                        .config
                        .has("name", "notification")
                        .value
                        .toIterator
                        .foreach { notificationConfig =>
                          val config = notificationConfig
                            .asOpt[Seq[NotificationConfig]]
                            .getOrElse(Nil)
                          organisationSrv
                            .get(organisation)
                            .users
                            .filter(_.config.hasNot("name", "notification"))
                            .toIterator
                            .foreach { user =>
                              executeForUser(user, config, audit, context, obj, organisation)
                            }
                        }
                    }
                  case (trigger, _) => logger.debug(s"Notification trigger ${trigger.name} is NOT applicable")
                }
            case _ =>
          }
      }
    case NotificationExecution(userId, auditId, notificationConfig) =>
      db.roTransaction { implicit graph =>
        auditSrv.getByIds(auditId).auditContextObjectOrganisation.getOrFail().foreach {
          case (audit, context, obj, Some(organisation)) =>
            for {
              user    <- userSrv.getOrFail(userId)
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
