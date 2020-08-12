package connectors.misp

import connectors.ConnectorModule
import javax.inject.Singleton
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport

@Singleton
class MispConnector extends ConnectorModule with AkkaGuiceSupport {
  private[MispConnector] lazy val logger = Logger(getClass)

  override def configure() {
    try {
      bind[MispSrv].asEagerSingleton()
      bindActor[UpdateMispAlertArtifactActor]("UpdateMispAlertArtifactActor")
      registerController[MispCtrl]
    } catch {
      case t: Throwable ⇒ logger.error("MISP connector is disabled because its configuration is invalid", t)
    }
  }
}
