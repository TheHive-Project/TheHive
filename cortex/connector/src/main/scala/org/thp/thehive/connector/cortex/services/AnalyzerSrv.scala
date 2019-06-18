package org.thp.thehive.connector.cortex.services

import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexConfig
import org.thp.cortex.dto.v0.OutputAnalyzer

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AnalyzerSrv @Inject()(cortexConfig: CortexConfig, implicit val ex: ExecutionContext) {

  /**
  * Lists the Cortex analyzers from all CortexClients
    * @return
    */
  def listAnalyzer: Future[Seq[OutputAnalyzer]] =
    Future
      .traverse(cortexConfig.instances)(_.listAnalyser)
      .map { listOfListOfAnalyzers ⇒
        val analysers = listOfListOfAnalyzers.flatten
        analysers
          .groupBy(_.name)
          .values
          .map(_.reduceLeft((a1, a2) ⇒ a1.copy(cortexIds = a1.cortexIds ::: a2.cortexIds)))
          .toSeq
      }
}
