package connectors.misp

import javax.inject.Singleton

import play.api.{ Configuration, Environment, Logger }

import connectors.ConnectorModule

class MispConnector(
    environment: Environment,
    configuration: Configuration) extends ConnectorModule {
  val log = Logger(getClass)

  def configure() {
    try {
//      val mispConfig = MispConfig(configuration)
//      bind[MispConfig].toInstance(mispConfig)
      bind[MispSrv].asEagerSingleton()
      registerController[MispCtrl]
    } catch {
      case t: Throwable => log.error("MISP connector is disabled because its configuration is invalid", t)
    }
  }
}