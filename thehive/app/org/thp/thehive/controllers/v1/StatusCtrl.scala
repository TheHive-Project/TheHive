package org.thp.thehive.controllers.v1

import akka.actor.ActorSystem
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.{Cluster, Member}
import com.softwaremill.tagging.@@
import org.thp.scalligraph.auth.{AuthCapability, AuthSrv, MultiAuthSrv}
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.UpdatableSchema
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.{Global, ScalligraphApplicationLoader}
import org.thp.thehive.models.User
import play.api.libs.json.{JsObject, JsString, Json, Writes}
import play.api.mvc.{AbstractController, Action, AnyContent, Results}

import scala.concurrent.duration.FiniteDuration
import scala.util.Success

class StatusCtrl(
    entrypoint: Entrypoint,
    appConfig: ApplicationConfig,
    authSrv: AuthSrv,
    schemas: Set[UpdatableSchema] @@ Global,
    system: ActorSystem
) {

  private def getVersion(c: Class[_]): String = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  val passwordConfig: ConfigItem[String, String] = appConfig.item[String]("datastore.attachment.password", "Password used to protect attachment ZIP")
  def password: String                           = passwordConfig.get
  val streamPollingDurationConfig: ConfigItem[FiniteDuration, FiniteDuration] =
    appConfig.item[FiniteDuration]("stream.longPolling.pollingDuration", "amount of time the UI have to wait before polling the stream")
  def streamPollingDuration: FiniteDuration = streamPollingDurationConfig.get
  val cluster: Cluster                      = Cluster(system)

  implicit val memberWrites: Writes[Member] = Writes[Member] { member =>
    Json.obj(
      "address" -> member.uniqueAddress.address.toString,
      "status"  -> member.status.toString,
      "roles"   -> member.roles
    )
  }
  implicit val clusterStateWrites: Writes[CurrentClusterState] = Writes[CurrentClusterState] { state =>
    Json.obj(
      "members"                -> state.members,
      "unreachable"            -> state.unreachable,
      "seenBy"                 -> state.seenBy.map(_.toString),
      "leader"                 -> state.leader.map(_.toString),
      "unreachableDataCenters" -> state.unreachableDataCenters
      //"roleLeaderMap"          -> state.roleLeaderMap,
    )
  }

  def get: Action[AnyContent] =
    entrypoint("status") { _ =>
      Success(
        Results.Ok(
          Json.obj(
            "versions" -> Json.obj(
              "Scalligraph" -> getVersion(classOf[ScalligraphApplicationLoader]),
              "TheHive"     -> getVersion(classOf[User]),
              "Play"        -> getVersion(classOf[AbstractController])
            ),
            "connectors" -> JsObject.empty,
            "config" -> Json.obj(
              "protectDownloadsWith" -> password,
              "authType" -> (authSrv match {
                case multiAuthSrv: MultiAuthSrv => Json.toJson(multiAuthSrv.providerNames)
                case _                          => JsString(authSrv.name)
              }),
              "capabilities"    -> authSrv.capabilities.map(c => JsString(c.toString)),
              "ssoAutoLogin"    -> authSrv.capabilities.contains(AuthCapability.sso),
              "pollingDuration" -> streamPollingDuration.toMillis
            ),
            "cluster" -> cluster.state,
            "schemaStatus" -> schemas.flatMap(_.schemaStatus).map { schemaStatus =>
              Json.obj(
                "name"            -> schemaStatus.name,
                "currentVersion"  -> schemaStatus.currentVersion,
                "expectedVersion" -> schemaStatus.expectedVersion,
                "error"           -> schemaStatus.error.map(_.getMessage)
              )
            }
          )
        )
      )
    }
}
