package org.thp.thehive.connector.cortex.services

import gremlin.scala.Graph
import javax.inject.Inject
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Entity
import org.thp.thehive.connector.cortex.models.{ActionOperation, ActionOperationStatus, AddTagToArtifact, AddTagToCase}
import org.thp.thehive.models.Case
import org.thp.thehive.services.{CaseSrv, ObservableSrv}
import play.api.Logger

import scala.util.{Failure, Try}

class ActionOperationSrv @Inject()(
    caseSrv: CaseSrv,
    observableSrv: ObservableSrv
) {
  private[ActionOperationSrv] lazy val logger = Logger(getClass)

  /**
    * Executes an operation from Cortex responder
    * report
    * @param entity the entity concerned by the operation
    * @param operation the operation to execute
    * @param relatedCase the related case if applicable
    * @param graph graph traversal
    * @param authContext auth for access check
    * @return
    */
  def execute(entity: Entity, operation: ActionOperation, relatedCase: Option[Case with Entity])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[ActionOperation] = {
    operation match {
      case AddTagToCase(tag, _, _) =>
        for {
          c <- Try(relatedCase.get)
          _ <- caseSrv.addTags(c, Set(tag))
        } yield operation.updateStatus(ActionOperationStatus.Success, "Success")

      case AddTagToArtifact(tag, _, _) =>
        for {
          obs <- observableSrv.get(entity._id).getOrFail()
          _   <- observableSrv.addTags(obs, Set(tag))
        } yield operation.updateStatus(ActionOperationStatus.Success, "Success")
      // TODO add rest of operations
      case x =>
        val m = s"ActionOperation ${x.toString} unknown"
        logger.error(m)
        Failure(new Exception(m))
    }
  } recover {
    case e =>
      logger.error("Operation execution fails", e)
      operation.updateStatus(ActionOperationStatus.Failure, e.getMessage)
  }
}
