package org.thp.thehive.connector.cortex.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import play.api.Logger
import play.api.libs.json.JsObject

import javax.inject.{Inject, Singleton}
import org.thp.cortex.dto.v0.OutputWorker
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.thehive.controllers.v0.Conversion.toObjectType
import org.thp.thehive.models.Permissions

@Singleton
class ResponderSrv @Inject()(
    connector: Connector,
    db: Database,
    entityHelper: EntityHelper,
    serviceHelper: ServiceHelper,
    implicit val ec: ExecutionContext
) {

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
  )(implicit authContext: AuthContext): Future[Map[OutputWorker, Seq[String]]] =
    for {
      entity        <- Future.fromTry(db.roTransaction(implicit graph => entityHelper.get(toObjectType(entityType), entityId, Permissions.manageAction)))
      (_, tlp, pap) <- Future.fromTry(db.roTransaction(implicit graph => entityHelper.entityInfo(entity)))
      responders <- Future
        .traverse(serviceHelper.availableCortexClients(connector.clients, authContext.organisation))(
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
    } yield serviceHelper.flattenList(responders).filter { case (w, _) => w.maxTlp >= tlp && w.maxPap >= pap }

  /**
    * Search responders, not used as of 08/19
    * @param query the raw query from frontend
    * @param authContext auth context for organisation filter
    * @return
    */
  def searchResponders(query: JsObject)(implicit authContext: AuthContext): Future[Map[OutputWorker, Seq[String]]] =
    Future
      .traverse(serviceHelper.availableCortexClients(connector.clients, authContext.organisation)) { client =>
        client
          .searchResponders(query)
          .transform {
            case Success(analyzers) => Success(analyzers.map(_ -> client.name))
            case Failure(error) =>
              logger.error(s"List Cortex analyzers fails on ${client.name}", error)
              Success(Nil)
          }
      }
      .map(serviceHelper.flattenList)
}
