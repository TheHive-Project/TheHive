package connectors.misp

import javax.inject.Singleton

import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.{ Configuration, Environment, Logger }

import connectors.ConnectorModule

@Singleton
class MispConnector(
    environment: Environment,
    configuration: Configuration) extends ConnectorModule with AkkaGuiceSupport {
  private[MispConnector] lazy val logger = Logger(getClass)

  def configure() {
    try {
      bind[MispSrv].asEagerSingleton()
      bindActor[UpdateMispAlertArtifactActor]("UpdateMispAlertArtifactActor")
      registerController[MispCtrl]
    }
    catch {
      case t: Throwable ⇒ logger.error("MISP connector is disabled because its configuration is invalid", t)
    }
  }
}