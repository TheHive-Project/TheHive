package org.thp.thehive.connector.cortex.services

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexConfig
import org.thp.cortex.dto.v0.OutputCortexWorker
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.thehive.models.{Organisation, Permissions}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class ResponderSrv @Inject()(
    cortexConfig: CortexConfig,
    db: Database,
    implicit val ec: ExecutionContext,
    entityHelper: EntityHelper,
    serviceHelper: ServiceHelper
) {
  import org.thp.thehive.connector.cortex.controllers.v0.ActionConversion._

  lazy val logger = Logger(getClass)

  /**
    * Gets a list of OutputCortexWorker from all available CortexClients
    * in relation with the entity type and id passed and
    * filtered by the allowed entity's tlp and pap
    *
    * @param entityType the entity
    * @param entityId its id
    * @param graph necessary graph db
    * @param authContext the auth context for visibility check
    * @return
    */
  def getRespondersByType(
      entityType: String,
      entityId: String
  )(implicit graph: Graph, authContext: AuthContext): Future[Map[OutputCortexWorker, Seq[String]]] =
    for {
      entity        <- Future.fromTry(entityHelper.get(toEntityType(entityType), entityId, Permissions.manageAction))
      (_, tlp, pap) <- Future.fromTry(db.roTransaction(implicit graph => entityHelper.entityInfo(entity)))
      responders <- Future
        .traverse(serviceHelper.availableCortexClients(cortexConfig, Organisation(authContext.organisation)))(
          client =>
            client
              .getRespondersByType(entityType)
              .transform {
                case Success(analyzers) => Success(analyzers.map(_ -> client.name))
                case Failure(error) =>
                  logger.error(s"List Cortex analyzers fails on ${client.name}", error)
                  Success(Nil)
              }
        )
    } yield responders.flatten // Seq[(worker, cortexId)]
      .groupBy(_._1.name)      // Map[workerName, Seq[(worker, cortexId)]]
      .values                  // Seq[Seq[(worker, cortexId)]]
      .map(a => a.head._1 -> a.map(_._2).toSeq) // Map[worker, Seq[CortexId] ]
      .filter(w => w._1.maxTlp.getOrElse(3L) >= tlp && w._1.maxPap.getOrElse(3L) >= pap) // TODO double check those default maxTlp and maxPap
      .toMap

}
