package org.thp.thehive.connector.cortex.services

import scala.concurrent.{ExecutionContext, Future}

import play.api.Logger

import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexConfig
import org.thp.cortex.dto.client.OutputCortexAnalyzer

@Singleton
class AnalyzerSrv @Inject()(cortexConfig: CortexConfig, implicit val ex: ExecutionContext) {

  lazy val logger = Logger(getClass)

  /**
    * Lists the Cortex analyzers from all CortexClients
    *
    * @return
    */
  def listAnalyzer: Future[Seq[OutputCortexAnalyzer]] =
    Future
      .traverse(cortexConfig.instances) { cortexInstance ⇒
        cortexInstance.listAnalyser.recover {
          case error ⇒
            logger.error(s"List Cortex analyzers fails on ${cortexInstance.name}", error)
            Nil
        }
      }
      .map { listOfListOfAnalyzers ⇒
        val analysers = listOfListOfAnalyzers.flatten
        analysers
          .groupBy(_.name)
          .values
          .map(_.reduceLeft((a1, a2) ⇒ a1.copy(cortexIds = Some(a1.cortexIds.getOrElse(Nil) ::: a2.cortexIds.getOrElse(Nil)))))
          .toSeq
      }
}
