package org.thp.thehive.services

import scala.collection.JavaConverters._
import scala.util.Try

import play.api.libs.json.JsValue

import gremlin.scala.{Graph, GremlinScala, Key, P, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.thehive.models._
import org.thp.thehive.services.notification.{NotificationSrv, Trigger}
import shapeless.HNil

@Singleton
class ConfigSrv @Inject()(
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv
)(implicit val db: Database)
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
            createdConfig <- create(Config(name, value))
            organisation  <- organisationSrv.get(organisationName).getOrFail()
            _             <- organisationConfigSrv.create(OrganisationConfig(), organisation, createdConfig)
          } yield ()
      }

    def getConfigValue(organisationName: String, name: String)(implicit graph: Graph): Option[Config with Entity] =
      organisationSrv
        .get(organisationName)
        .config
        .has(Key("name"), P.eq(name))
        .headOption()
  }

  object user {

    def setConfigValue(userName: String, name: String, value: JsValue)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      getConfigValue(userName, name) match {
        case Some(config) => get(config).update("value" -> value).map(_ => ())
        case None =>
          for {
            createdConfig <- create(Config(name, value))
            user          <- userSrv.get(userName).getOrFail()
            _             <- userConfigSrv.create(UserConfig(), user, createdConfig)
          } yield ()
      }

    def getConfigValue(userName: String, name: String)(implicit graph: Graph): Option[Config with Entity] =
      userSrv
        .get(userName)
        .config
        .has(Key("name"), P.eq(name))
        .headOption()
  }
}

class ConfigSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Config, ConfigSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ConfigSteps = new ConfigSteps(raw)

  def triggerMap(notificationSrv: NotificationSrv): Map[String, Map[Trigger, (Boolean, Seq[String])]] = {
    def notificationRaw: GremlinScala.Aux[Vertex, HNil] =
      raw
        .clone()
        .has(Key[String]("name"), P.eq[String]("notification"))
        .asInstanceOf[GremlinScala.Aux[Vertex, HNil]]

    val organisationTriggers: Iterator[(String, Trigger, Either[Boolean, String])] = for {
      (notifConfig, orgId) <- notificationRaw
        .as("config")
        .in("OrganisationConfig")
        .id()
        .as("orgId")
        .select
        .traversal
        .asScala
      trigger <- notificationSrv.getTriggers(notifConfig.value[String]("value"))
    } yield (orgId.toString, trigger, Left(true))

    val userTriggers: Iterator[(String, Trigger, Either[Boolean, String])] = for {
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
    } yield (orgId.toString, trigger, Right(user.id().toString))
//    val userTriggers: Iterator[(String, Trigger, Either[Boolean, String])] = for {
//      (userOrgs, notifConfig) <- notificationRaw
//        .project(
//          _.apply(
//            By(
//              __[Vertex]
//                .inTo[UserConfig]
//                .project(
//                  _.apply(By[AnyRef](T.id))                                                  // user
//                    .and(By(__[Vertex].outTo[UserRole].outTo[RoleOrganisation].id().fold())) // orgs
//                )
//                .fold()
//            )
//          ).and(By(Key[String]("value"))) // notif
//        )
//        .traversal
//        .asScala
//      _ = logger.debug(s"userTriggers for $notifConfig: ${userOrgs.asScala} ")
//      (userId, orgIds) <- userOrgs.asScala
//      orgId            <- orgIds.asScala
//      trigger          <- notificationSrv.getTriggers(notifConfig)
//    } yield (orgId.toString, trigger, Right(userId.toString))

    (organisationTriggers ++ userTriggers)
      .toSeq
      .groupBy(_._1)
      .mapValues { tuple =>
        tuple
          .groupBy(_._2)
          .mapValues { tuple2 =>
            val inOrg   = tuple2.exists(_._3.isLeft)
            val userIds = tuple2.collect { case (_, _, Right(userId)) => userId }
            (inOrg, userIds)
          }
          .toMap
      }
      .toMap
  }

//  def triggerByOrganisation: Map[String, Set[Trigger]] =
//    raw
//      .group(
//        By(__[Vertex].coalesce(_.inTo[OrganisationConfig], _.inTo[UserConfig].outTo[UserRole].outTo[RoleOrganisation]).value[String]("name")),
//        By(Key[String]("value"))
//      )
//      .traversal
//      .asScala
//      .foldLeft(Map.empty[String, Set[Trigger]]) {
//        case (acc, m) =>
//          val orgTriggers = m.asScala.flatMap {
//            case (organisationName, notifConfigs) =>
//              notifConfigs.asScala.map(s => organisationName -> Trigger(Json.parse(s)).toSet)
//          }
//          acc ++ orgTriggers
//      }
}
