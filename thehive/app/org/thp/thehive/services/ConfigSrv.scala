package org.thp.thehive.services

import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.{Graph, Vertex}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, StepLabel, Traversal}
import org.thp.thehive.models._
import org.thp.thehive.services.ConfigOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services.notification.NotificationSrv
import org.thp.thehive.services.notification.triggers.Trigger
import play.api.libs.json.{JsValue, Reads}

import scala.util.Try

@Singleton
class ConfigSrv @Inject() (
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv
)(@Named("with-thehive-schema") implicit val db: Database)
    extends VertexSrv[Config] {
  val organisationConfigSrv = new EdgeSrv[OrganisationConfig, Organisation, Config]
  val userConfigSrv         = new EdgeSrv[UserConfig, User, Config]

  def triggerMap(notificationSrv: NotificationSrv)(implicit graph: Graph): Map[String, Map[Trigger, (Boolean, Seq[String])]] =
    startTraversal.triggerMap(notificationSrv)

  object organisation {

    def setConfigValue(organisationName: String, name: String, value: JsValue)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      getConfigValue(organisationName, name) match {
        case Some(config) => get(config).update(_.value, value).domainMap(_ => ()).getOrFail("Config")
        case None =>
          for {
            createdConfig <- createEntity(Config(name, value))
            organisation  <- organisationSrv.get(organisationName).getOrFail("Organisation")
            _             <- organisationConfigSrv.create(OrganisationConfig(), organisation, createdConfig)
          } yield ()
      }

    def getConfigValue(organisationName: String, name: String)(implicit graph: Graph): Option[Config with Entity] =
      organisationSrv
        .get(organisationName)
        .config
        .has("name", name)
        .headOption
  }

  object user {

    def setConfigValue(userName: String, name: String, value: JsValue)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      getConfigValue(userName, name) match {
        case Some(config) => get(config).update(_.value, value).domainMap(_ => ()).getOrFail("Config")
        case None =>
          for {
            createdConfig <- createEntity(Config(name, value))
            user          <- userSrv.get(userName).getOrFail("User")
            _             <- userConfigSrv.create(UserConfig(), user, createdConfig)
          } yield ()
      }

    def getConfigValue(userName: String, name: String)(implicit graph: Graph): Option[Config with Entity] =
      userSrv
        .get(userName)
        .config
        .has(_.name, name)
        .headOption
  }
}

object ConfigOps {

  implicit class ConfigOpsDefs(traversal: Traversal.V[Config]) {
    def triggerMap(notificationSrv: NotificationSrv): Map[String, Map[Trigger, (Boolean, Seq[String])]] = {

      // Traversal of configuration version of type "notification"
      def notificationRaw: Traversal[Config with Entity, Vertex, Converter[Config with Entity, Vertex]] =
        traversal
          .clone()
          .has(_.name, "notification")

      // Retrieve triggers configured for each organisation
      val organisationTriggers: Iterator[(String, Trigger, Option[String])] = {
        val configLabel         = StepLabel.v[Config]
        val organisationIdLabel = StepLabel[String, AnyRef, Converter[String, AnyRef]]
        for {
          (notifConfig, orgId) <- notificationRaw
            .as(configLabel)
            .in[OrganisationConfig]
            ._id
            .as(organisationIdLabel)
            .select((configLabel, organisationIdLabel))
            .toIterator
          //      cfg     <- notificationSrv.getConfig(notifConfig.value[String]("value"))
          //      trigger <- notificationSrv.getTrigger(cfg.triggerConfig).toOption
          trigger <- notificationSrv.getTriggers(notifConfig.value)
        } yield (orgId, trigger, None: Option[String])
      }

      // Retrieve triggers configured for each user
      val userTriggers: Iterator[(String, Trigger, Option[String])] = {
        val configLabel         = StepLabel.v[Config]
        val userLabel           = StepLabel.v[User]
        val organisationIdLabel = StepLabel[String, AnyRef, Converter[String, AnyRef]]
        for {
          (notifConfig, user, orgId) <- notificationRaw
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
        .mapValues { tuple =>
          tuple
            .groupBy(_._2)
            .mapValues { tuple2 =>
              val inOrg   = tuple2.exists(_._3.isEmpty)
              val userIds = tuple2.flatMap(_._3.toSeq)
              (inOrg, userIds)
            }
        }
    }

    def getValue[A: Reads](name: String): Traversal[JsValue, String, Converter[JsValue, String]] = traversal.has(_.name, name).value(_.value)
  }
}
