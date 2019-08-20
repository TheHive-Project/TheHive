package org.thp.thehive.connector.cortex.services

import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexConfig
import org.thp.cortex.dto.v0.OutputCortexWorker
import org.thp.scalligraph.auth.AuthContext
import org.thp.thehive.models.Organisation
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class AnalyzerSrv @Inject()(cortexConfig: CortexConfig, serviceHelper: ServiceHelper) {

  lazy val logger = Logger(getClass)

  /**
    * Lists the Cortex analyzers from all CortexClients
    *
    * @return
    */
  def listAnalyzer(implicit authContext: AuthContext): Future[Map[OutputCortexWorker, Seq[String]]] =
    Future
      .traverse(serviceHelper.availableCortexClients(cortexConfig, Organisation(authContext.organisation))) { client =>
        client
          .listAnalyser
          .transform {
            case Success(analyzers) => Success(analyzers.map(_ -> client.name))
            case Failure(error) =>
              logger.error(s"List Cortex analyzers fails on ${client.name}", error)
              Success(Nil)
          }
      }
      .map { listOfListOfAnalyzers =>
        listOfListOfAnalyzers // Iterable[Seq[(worker, cortexId)]]
        .flatten              // Seq[(worker, cortexId)]
          .groupBy(_._1.name) // Map[workerName, Seq[(worker, cortexId)]]
          .values             // Seq[Seq[(worker, cortexId)]]
          .map(a => a.head._1 -> a.map(_._2).toSeq) // Map[worker, Seq[CortexId] ]
          .toMap
      }

  def getAnalyzer(id: String)(implicit authContext: AuthContext): Future[(OutputCortexWorker, Seq[String])] =
    Future
      .traverse(serviceHelper.availableCortexClients(cortexConfig, Organisation(authContext.organisation))) { client =>
        client
          .getAnalyzer(id)
          .map(_ -> client.name)
      }
      .map(
        analyzerByClients =>
          analyzerByClients
            .groupBy(_._1.name)
            .values // Seq[Seq[(worker, cortexId)]]
            .map(a => a.head._1 -> a.map(_._2).toSeq) // Map[worker, Seq[CortexId] ]
            .toMap
            .head
      )
}
