package org.thp.cortex.client

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.thp.cortex.dto.v0._
import play.api.Logger

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class CortexClient(baseUrl: String, refreshDelay: FiniteDuration, maxRetryOnError: Int)(
    implicit ws: CustomWSAPI,
    auth: Authentication,
    system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
) {
  lazy val job            = new BaseClient[InputArtifact, OutputJob](s"$baseUrl/api/job")
  lazy val analyser       = new BaseClient[InputAnalyzer, OutputAnalyzer](s"$baseUrl/api/analyzer")
  private lazy val logger = Logger(getClass)

  def retry[T](n: Int = maxRetryOnError, delay: FiniteDuration = refreshDelay)(f: ⇒ Future[T]): Future[T] = f recoverWith {
    case e if n > 1 ⇒
      logger.warn(s"CortexClient failed (${e.getMessage}) for $f at attempt $n, retrying in $delay")
      retry(n - 1)(f)
  }

  def listAnalyser: Future[Seq[OutputAnalyzer]] = retry()(analyser.list)
}
