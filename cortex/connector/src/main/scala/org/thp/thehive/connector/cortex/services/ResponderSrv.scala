package org.thp.thehive.connector.cortex.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

import play.api.Logger
import play.api.libs.json.JsObject

import javax.inject.{Inject, Singleton}
import org.thp.cortex.dto.v0.OutputCortexWorker
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.thehive.models.{Organisation, Permissions}

@Singleton
class ResponderSrv @Inject()(
    connector: Connector,
    db: Database,
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
    * @param authContext the auth context for visibility check
    * @return
    */
  def getRespondersByType(
      entityType: String,
      entityId: String
  )(implicit authContext: AuthContext): Future[Map[OutputCortexWorker, Seq[String]]] =
    for {
      entity        <- Future.fromTry(db.roTransaction(implicit graph => entityHelper.get(toEntityType(entityType), entityId, Permissions.manageAction)))
      (_, tlp, pap) <- Future.fromTry(db.roTransaction(implicit graph => entityHelper.entityInfo(entity)))
      responders <- Future
        .traverse(serviceHelper.availableCortexClients(connector.clients, Organisation(authContext.organisation)))(
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
    } yield serviceHelper.flattenList(responders, w => w._1.maxTlp >= tlp && w._1.maxPap >= pap)

  /**
    * Search responders, not used as of 08/19
    * @param query the raw query from frontend
    * @param authContext auth context for organisation filter
    * @return
    */
  def searchResponders(query: JsObject)(implicit authContext: AuthContext): Future[Map[OutputCortexWorker, Seq[String]]] =
    Future
      .traverse(serviceHelper.availableCortexClients(connector.clients, Organisation(authContext.organisation)))(
        client =>
          client
            .searchResponders(query)
            .transform {
              case Success(analyzers) => Success(analyzers.map(_ -> client.name))
              case Failure(error) =>
                logger.error(s"List Cortex analyzers fails on ${client.name}", error)
                Success(Nil)
            }
      )
      .map(serviceHelper.flattenList(_, _ => true))
}
