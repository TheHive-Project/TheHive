package org.thp.thehive.connector.cortex.services

import gremlin.scala.Graph
import javax.inject.Inject
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.thehive.connector.cortex.models.{ActionOperation, ActionOperationStatus, AddTagToCase}
import org.thp.thehive.models.Case
import org.thp.thehive.services.CaseSrv
import play.api.Logger

import scala.util.Try

class ActionOperationSrv @Inject()(
    implicit db: Database,
    caseSrv: CaseSrv
) {
  private[ActionOperationSrv] lazy val logger = Logger(getClass)

  def execute(entity: Entity, operation: ActionOperation, relatedCase: Option[Case with Entity])(implicit graph: Graph, authContext: AuthContext) = {
    operation match {
      case AddTagToCase(tag, _, _) =>
        for {
          c <- Try(relatedCase.get)
          _ <- caseSrv.get(c._id).update("tags" -> (c.tags + tag))
        } yield operation.updateStatus(ActionOperationStatus.Success, "Success")
      case _ => ???
    }
  } recover {
    case e =>
      logger.error("Operation execution fails", e)
      operation.updateStatus(ActionOperationStatus.Failure, e.getMessage)
  }
}
