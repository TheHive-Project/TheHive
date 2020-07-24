package org.thp.thehive.services

import gremlin.scala.{Graph, GremlinScala, Key, P, Vertex}
import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.thehive.models._
import org.thp.thehive.services.notification.NotificationSrv
import org.thp.thehive.services.notification.triggers.Trigger
import play.api.libs.json.{JsValue, Reads}
import shapeless.HNil

import scala.collection.JavaConverters._
import scala.util.Try

@Singleton
class ConfigSrv @Inject() (
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv
)(@Named("with-thehive-schema") implicit val db: Database)
    extends VertexSrv[Config, ConfigSteps] {
  val organisationConfigSrv = new EdgeSrv[OrganisationConfig, Organisation, Config]
  val userConfigSrv         = new EdgeSrv[UserConfig, User, Config]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ConfigSteps = new ConfigSteps(raw)

  def triggerMap(notificationSrv: NotificationSrv)(implicit graph: Graph): Map[String, Map[Trigger, (Boolean, Seq[String])]] =
    initSteps.triggerMap(notificationSrv)

  object organisation {

    def setConfigValue(organisationName: String, name: String, value: JsValue)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      getConfigValue(organisationName, name) match {
        case Some(config) => get(config).update("value" -> value).map(_ => ())
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
        .headOption()
  }

  object user {

    def setConfigValue(userName: String, name: String, value: JsValue)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      getConfigValue(userName, name) match {
        case Some(config) => get(config).update("value" -> value).map(_ => ())
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
        .has("name", name)
        .headOption()
  }
}

@EntitySteps[Config]
class ConfigSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph) extends VertexSteps[Config](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): ConfigSteps = new ConfigSteps(newRaw)
  override def newInstance(): ConfigSteps                             = new ConfigSteps(raw.clone())

  def triggerMap(notificationSrv: NotificationSrv): Map[String, Map[Trigger, (Boolean, Seq[String])]] = {

    // Traversal of configuration version of type "notification"
    def notificationRaw: GremlinScala.Aux[Vertex, HNil] =
      raw
        .clone()
        .has(Key[String]("name"), P.eq[String]("notification"))
        .asInstanceOf[GremlinScala.Aux[Vertex, HNil]]

    // Retrieve triggers configured for each organisation
    val organisationTriggers: Iterator[(String, Trigger, Option[String])] = for {
      (notifConfig, orgId) <- notificationRaw
        .as("config")
        .in("OrganisationConfig")
        .id()
        .as("orgId")
        .select
        .traversal
        .asScala
//      cfg     <- notificationSrv.getConfig(notifConfig.value[String]("value"))
//      trigger <- notificationSrv.getTrigger(cfg.triggerConfig).toOption
      trigger <- notificationSrv.getTriggers(notifConfig.value[String]("value"))
    } yield (orgId.toString, trigger, None)

    // Retrieve triggers configured for each user
    val userTriggers: Iterator[(String, Trigger, Option[String])] = for {
      (notifConfig, user, orgId) <- notificationRaw
        .as("config")
        .in("UserConfig")
        .as("user")
        .out("UserRole")
        .out("RoleOrganisation")
        .id()
        .as("orgId")
        .select()
        .traversal
        .asScala
      trigger <- notificationSrv.getTriggers(notifConfig.value[String]("value"))
    } yield (orgId.toString, trigger, Some(user.id().toString))

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

  def getValue[A: Reads](name: String): Traversal[JsValue, String] = this.has("name", name).value
}
