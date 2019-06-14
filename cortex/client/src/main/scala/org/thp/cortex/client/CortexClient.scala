package org.thp.cortex.client

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.thp.cortex.dto.v0._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class CortexClient(baseUrl: String, refreshDelay: FiniteDuration, maxRetryOnError: Int)(implicit ws: CustomWSAPI, system: ActorSystem,
                                                                                        ec: ExecutionContext,
                                                                                        mat: Materializer) {
  lazy val job = new BaseClient[InputArtifact, OutputJob](s"$baseUrl/api/job")

  lazy val analyser = new BaseClient[InputAnalyzer, OutputAnalyzer](s"$baseUrl/api/analyzer")

  def listAnalyser(attempts: Int = 0, maxRetries: Int = maxRetryOnError) = attempts match {
    case 0 =>
  }
}
