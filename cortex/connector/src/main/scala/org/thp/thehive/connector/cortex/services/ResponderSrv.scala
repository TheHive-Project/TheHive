package org.thp.thehive.connector.cortex.services

import com.google.inject.name.Named
import javax.inject.{Inject, Singleton}
import org.thp.cortex.dto.v0.OutputWorker
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.thehive.controllers.v0.Conversion.toObjectType
import org.thp.thehive.models.Permissions
import play.api.Logger
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class ResponderSrv @Inject() (
    connector: Connector,
    @Named("with-thehive-cortex-schema") db: Database,
    entityHelper: EntityHelper,
    serviceHelper: ServiceHelper,
    implicit val ec: ExecutionContext
) {

  lazy val logger: Logger = Logger(getClass)

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
      entityId: EntityIdOrName
  )(implicit authContext: AuthContext): Future[Map[OutputWorker, Seq[String]]] =
    for {
      entity        <- Future.fromTry(db.roTransaction(implicit graph => entityHelper.get(toObjectType(entityType), entityId, Permissions.manageAction)))
      (_, tlp, pap) <- Future.fromTry(db.roTransaction(implicit graph => entityHelper.entityInfo(entity)))
      responders <-
        Future
          .traverse(serviceHelper.availableCortexClients(connector.clients, authContext.organisation))(client =>
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

  def getRespondersByName(responderName: String, organisation: EntityIdOrName): Future[Map[OutputWorker, Seq[String]]] =
    searchResponders(Json.obj("query" -> Json.obj("_field" -> "name", "_value" -> responderName)), organisation)

  /**
    * Search responders
    * @param query the raw query from frontend
    * @param authContext auth context for organisation filter
    * @return
    */
  def searchResponders(query: JsObject)(implicit authContext: AuthContext): Future[Map[OutputWorker, Seq[String]]] =
    searchResponders(query, authContext.organisation)

  def searchResponders(query: JsObject, organisation: EntityIdOrName): Future[Map[OutputWorker, Seq[String]]] =
    Future
      .traverse(serviceHelper.availableCortexClients(connector.clients, organisation)) { client =>
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
