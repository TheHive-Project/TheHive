package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.scalligraph.traversal.{Converter, Graph, StepLabel, Traversal}
import org.thp.scalligraph.{EntityId, EntityIdOrName}
import org.thp.thehive.models._
import org.thp.thehive.services.notification.NotificationSrv
import org.thp.thehive.services.notification.triggers.Trigger
import play.api.libs.json.{JsValue, Reads}
import java.util.Date
import scala.util.Try

class ConfigSrv(
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    db: Database
) extends VertexSrv[Config]
    with TheHiveOpsNoDeps {
  val organisationConfigSrv = new EdgeSrv[OrganisationConfig, Organisation, Config]
  val userConfigSrv         = new EdgeSrv[UserConfig, User, Config]

  def triggerMap(notificationSrv: NotificationSrv)(implicit graph: Graph): Map[EntityId, Map[Trigger, (Boolean, Seq[EntityId])]] =
    startTraversal.triggerMap(notificationSrv)

  object organisation {

    def setConfigValue(organisationName: EntityIdOrName, name: String, value: JsValue)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      getConfigValue(organisationName, name) match {
        case Some(config) =>
          get(config)
            .update(_.value, value)
            .update(_._updatedAt, Some(new Date))
            .update(_._updatedBy, Some(authContext.userId))
            .domainMap(_ => ())
            .getOrFail("Config")
        case None =>
          for {
            createdConfig <- createEntity(Config(name, value))
            organisation  <- organisationSrv.get(organisationName).getOrFail("Organisation")
            _             <- organisationConfigSrv.create(OrganisationConfig(), organisation, createdConfig)
          } yield ()
      }

    def getConfigValue(organisationName: EntityIdOrName, name: String)(implicit graph: Graph): Option[Config with Entity] =
      organisationSrv
        .get(organisationName)
        .config
        .has(_.name, name)
        .headOption
  }

  object user {

    def setConfigValue(userName: EntityIdOrName, name: String, value: JsValue)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      getConfigValue(userName, name) match {
        case Some(config) =>
          get(config)
            .update(_.value, value)
            .update(_._updatedAt, Some(new Date))
            .update(_._updatedBy, Some(authContext.userId))
            .domainMap(_ => ())
            .getOrFail("Config")
        case None =>
          for {
            createdConfig <- createEntity(Config(name, value))
            user          <- userSrv.get(userName).getOrFail("User")
            _             <- userConfigSrv.create(UserConfig(), user, createdConfig)
          } yield ()
      }

    def getConfigValue(userName: EntityIdOrName, name: String)(implicit graph: Graph): Option[Config with Entity] =
      userSrv
        .get(userName)
        .config
        .has(_.name, name)
        .headOption
  }
}

trait ConfigOps { _: TheHiveOpsNoDeps =>

  implicit class ConfigOpsDefs(traversal: Traversal.V[Config]) {
    def triggerMap(notificationSrv: NotificationSrv): Map[EntityId, Map[Trigger, (Boolean, Seq[EntityId])]] = {

      // Traversal of configuration version of type "notification"
      def notificationRaw: Traversal.V[Config] =
        traversal
          .clone()
          .has(_.name, "notification")

      // Retrieve triggers configured for each organisation
      val organisationTriggers: Iterator[(EntityId, Trigger, Option[EntityId])] = {
        val configLabel         = StepLabel.v[Config]
        val organisationIdLabel = StepLabel[EntityId, AnyRef, Converter[EntityId, AnyRef]]
        for {
          (notifConfig, orgId) <-
            notificationRaw
              .as(configLabel)
              .in[OrganisationConfig]
              ._id
              .as(organisationIdLabel)
              .select((configLabel, organisationIdLabel))
              .toIterator
          //      cfg     <- notificationSrv.getConfig(notifConfig.value[String]("value"))
          //      trigger <- notificationSrv.getTrigger(cfg.triggerConfig).toOption
          trigger <- notificationSrv.getTriggers(notifConfig.value)
        } yield (orgId, trigger, None: Option[EntityId])
      }

      // Retrieve triggers configured for each user
      val userTriggers: Iterator[(EntityId, Trigger, Option[EntityId])] = {
        val configLabel         = StepLabel.v[Config]
        val userLabel           = StepLabel.v[User]
        val organisationIdLabel = StepLabel[EntityId, AnyRef, Converter[EntityId, AnyRef]]
        for {
          (notifConfig, user, orgId) <-
            notificationRaw
              .as(configLabel)
              .in[UserConfig]
              .v[User]
              .as(userLabel)
              .out[UserRole]
              .out[RoleOrganisation]
              ._id
              .as(organisationIdLabel)
              .select((configLabel, userLabel, organisationIdLabel))
              .toIterator
          trigger <- notificationSrv.getTriggers(notifConfig.value)
        } yield (orgId, trigger, Some(user._id))
      }

      (organisationTriggers ++ userTriggers)
        .toSeq
        .groupBy(_._1)
        .view
        .mapValues { tuple =>
          tuple
            .groupBy(_._2)
            .view
            .mapValues { tuple2 =>
              val inOrg   = tuple2.exists(_._3.isEmpty)
              val userIds = tuple2.flatMap(_._3.toSeq)
              (inOrg, userIds)
            }
            .toMap
        }
        .toMap
    }

    def getValue[A: Reads](name: String): Traversal[JsValue, String, Converter[JsValue, String]] = traversal.has(_.name, name).value(_.value)
  }
}
