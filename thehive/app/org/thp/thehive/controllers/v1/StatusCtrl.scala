package org.thp.thehive.controllers.v1

import akka.actor.ActorSystem
import akka.cluster.{Cluster, Member}
import akka.cluster.ClusterEvent.CurrentClusterState

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.ScalligraphApplicationLoader
import org.thp.scalligraph.auth.{AuthCapability, AuthSrv, MultiAuthSrv}
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.TheHiveModule
import play.api.libs.json.{JsObject, JsString, Json, Writes}
import play.api.mvc.{AbstractController, Action, AnyContent, Results}

import scala.util.Success

@Singleton
class StatusCtrl @Inject() (entrypoint: Entrypoint, appConfig: ApplicationConfig, authSrv: AuthSrv, system: ActorSystem) {

  private def getVersion(c: Class[_]): String = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  val passwordConfig: ConfigItem[String, String] = appConfig.item[String]("datastore.attachment.password", "Password used to protect attachment ZIP")
  def password: String                           = passwordConfig.get
  val cluster: Cluster                           = Cluster(system)

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
              "TheHive"     -> getVersion(classOf[TheHiveModule]),
              "Play"        -> getVersion(classOf[AbstractController])
            ),
            "connectors" -> JsObject.empty,
            "config" -> Json.obj(
              "protectDownloadsWith" -> password,
              "authType" -> (authSrv match {
                case multiAuthSrv: MultiAuthSrv => Json.toJson(multiAuthSrv.providerNames)
                case _                          => JsString(authSrv.name)
              }),
              "capabilities" -> authSrv.capabilities.map(c => JsString(c.toString)),
              "ssoAutoLogin" -> authSrv.capabilities.contains(AuthCapability.sso)
            ),
            "cluster" -> cluster.state
          )
        )
      )
    }

}
