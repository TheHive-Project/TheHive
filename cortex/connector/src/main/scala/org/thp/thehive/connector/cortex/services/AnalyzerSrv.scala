package org.thp.thehive.connector.cortex.services

import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexConfig
import org.thp.cortex.dto.v0.{AnalyzerConversion, OutputAnalyzer}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AnalyzerSrv @Inject()(cortexConfig: CortexConfig, implicit val ex: ExecutionContext, system: ActorSystem, mat: Materializer)
    extends AnalyzerConversion {

  /**
    * Lists the Cortex analyzers from all CortexClients
    *
    * @return
    */
  def listAnalyzer: Future[Seq[OutputAnalyzer]] =
    Future
      .sequence(cortexConfig.instances.map(_.listAnalyser))
      .map { listOfListOfAnalyzers ⇒
        val analysers = listOfListOfAnalyzers.flatten
        analysers
          .groupBy(_.name)
          .values
          .map(_.reduceLeft((a1, a2) ⇒
            a1.copy(cortexIds = Some(a1.cortexIds.getOrElse(Nil) ::: a2.cortexIds.getOrElse(Nil)))
          ))
          .map(toOutputAnalyzer)
          .toSeq
      }
}
