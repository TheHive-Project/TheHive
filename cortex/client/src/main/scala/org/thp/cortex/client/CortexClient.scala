package org.thp.cortex.client

import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.Materializer
import org.thp.cortex.dto.v0._
import play.api.Logger

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class CortexClient(name: String, baseUrl: String, refreshDelay: FiniteDuration, maxRetryOnError: Int)(
    implicit ws: CustomWSAPI,
    auth: Authentication,
    system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
) {
  lazy val job      = new BaseClient[InputArtifact, OutputJob](s"$baseUrl/api/job")
  lazy val analyser = new BaseClient[InputAnalyzer, OutputAnalyzer](s"$baseUrl/api/analyzer")
  lazy val logger   = Logger(getClass)

  /**
    * Request retry-er according to conf parameters
    *
    * @param n the nb of retries
    * @param delay the delay between retries
    * @param f the lambda to try
    * @tparam T the return type
    * @return
    */
  def retry[T](n: Int = maxRetryOnError, delay: FiniteDuration = refreshDelay)(f: ⇒ Future[T], callName: String): Future[T] = f recoverWith {
    case e if n > 1 ⇒
      logger.warn(s"CortexClient $name failed (${e.getMessage}) for $callName at attempt $n, retrying in $delay")
      after(delay, system.scheduler)(retry(n - 1)(f, name))
  }

  /**
    * GET analysers endpoint
    *
    * @return
    */
  def listAnalyser: Future[Seq[OutputAnalyzer]] = retry()(analyser.list, "listAnalyzer")
}
