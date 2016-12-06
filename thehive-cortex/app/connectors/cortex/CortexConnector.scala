package connectors.cortex

import javax.inject.Singleton

import play.api.{ Configuration, Environment, Logger }

import connectors.ConnectorModule

class CortexConnector(
    environment: Environment,
    configuration: Configuration) extends ConnectorModule {
  val log = Logger(getClass)

  def configure() {
    try {
      registerController[CortextCtrl]
    }
    catch {
      case t: Throwable ⇒ log.error("Corte connector is disabled because its configuration is invalid", t)
    }
  }
}
