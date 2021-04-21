package org.thp.thehive.connector.cortex.services

import org.thp.cortex.client.{CortexClient, CortexClientConfig}
import org.thp.cortex.dto.v0.{OutputWorker => CortexWorker}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.services.config.ConfigItem
import org.thp.scalligraph.{EntityIdOrName, NotFoundError}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class AnalyzerSrv(
    serviceHelper: ServiceHelper,
    clientsConfig: ConfigItem[Seq[CortexClientConfig], Seq[CortexClient]],
    implicit val ec: ExecutionContext
) {

  lazy val logger: Logger = Logger(getClass)

  def clients: Seq[CortexClient] = clientsConfig.get

  /**
    * Lists the Cortex analyzers from all CortexClients
    *
    * @return
    */
  def listAnalyzer(range: Option[String])(implicit authContext: AuthContext): Future[Map[CortexWorker, Seq[String]]] =
    Future
      .traverse(serviceHelper.availableCortexClients(clients, authContext.organisation)) { client =>
        client
          .listAnalyser(range)
          .transform {
            case Success(analyzers) => Success(analyzers.map(_ -> client.name))
            case Failure(error) =>
              logger.error(s"List Cortex analyzers fails on ${client.name}", error)
              Success(Nil)
          }
      }
      .map(serviceHelper.flattenList)

  def listAnalyzerByType(dataType: String)(implicit authContext: AuthContext): Future[Map[CortexWorker, Seq[String]]] =
    Future
      .traverse(serviceHelper.availableCortexClients(clients, authContext.organisation)) { client =>
        client
          .listAnalyzersByType(dataType)
          .transform {
            case Success(analyzers) => Success(analyzers.map(_ -> client.name))
            case Failure(error) =>
              logger.error(s"List Cortex analyzers by dataType fails on ${client.name}", error)
              Success(Nil)
          }
      }
      .map(serviceHelper.flattenList)

  def getAnalyzer(id: String)(implicit authContext: AuthContext): Future[(CortexWorker, Seq[String])] =
    Future
      .traverse(serviceHelper.availableCortexClients(clients, authContext.organisation)) { client =>
        client
          .getAnalyzer(id)
          .map(_ -> client.name)
      }
      .flatMap { analyzerByClients =>
        analyzerByClients     // Seq[(worker, cortexId)]
          .groupBy(_._1.name) // Map[CortexId, Seq[(worker, cortexId)]]
          .values             // Seq[Seq[(worker, cortexId)]]
          .map(a => a.head._1 -> a.map(_._2).toSeq) // Map[worker, Seq[CortexId]]
          .headOption
          .fold[Future[(CortexWorker, Seq[String])]](Future.failed(NotFoundError(s"Analyzer $id not found")))(Future.successful)
      }

  def getAnalyzerByName(analyzerName: String, organisation: EntityIdOrName): Future[Map[CortexWorker, Seq[String]]] =
    searchAnalyzers(Json.obj("query" -> Json.obj("_field" -> "name", "_value" -> analyzerName)), organisation)

  def searchAnalyzers(query: JsObject)(implicit authContext: AuthContext): Future[Map[CortexWorker, Seq[String]]] =
    searchAnalyzers(query, authContext.organisation)

  def searchAnalyzers(query: JsObject, organisation: EntityIdOrName): Future[Map[CortexWorker, Seq[String]]] =
    Future
      .traverse(serviceHelper.availableCortexClients(clients, organisation)) { client =>
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
