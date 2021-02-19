package connectors.cortex

import connectors.ConnectorModule
import connectors.cortex.controllers.CortexCtrl
import connectors.cortex.services.JobReplicateActor
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport

class CortexConnector extends ConnectorModule with AkkaGuiceSupport {
  private[CortexConnector] lazy val logger = Logger(getClass)

  override def configure() {
    try {
      registerController[CortexCtrl]
      bindActor[JobReplicateActor]("JobReplicateActor")
    } catch {
      case t: Throwable => logger.error("Cortex connector is disabled because its configuration is invalid", t)
    }
  }
}
