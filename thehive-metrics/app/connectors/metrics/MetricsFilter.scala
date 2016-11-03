package connectors.metrics

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import akka.stream.Materializer

import play.api.http.Status
import play.api.mvc.{ Filter, RequestHeader, Result, Results }

import com.codahale.metrics.{ Counter, Meter, MetricRegistry }
import com.codahale.metrics.MetricRegistry.name
import com.codahale.metrics.Timer

trait MetricsFilter extends Filter

@Singleton
class NoMetricsFilter @Inject() (implicit val mat: Materializer) extends MetricsFilter {
  def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader) = f(rh)
}

@Singleton
class MetricsFilterImpl @Inject() (metricsModule: Metrics, implicit val mat: Materializer, implicit val ec: ExecutionContext) extends MetricsFilter {

  /**
   * Specify a meaningful prefix for metrics
   *
   * Defaults to classOf[MetricsFilter].getName for backward compatibility as
   * this was the original set value.
   *
   */
  def labelPrefix: String = classOf[MetricsFilter].getName

  /**
   * Specify which HTTP status codes have individual metrics
   *
   * Statuses not specified here are grouped together under otherStatuses
   *
   * Defaults to 200, 400, 401, 403, 404, 409, 201, 304, 307, 500, which is compatible
   * with prior releases.
   */
  def knownStatuses = Seq(Status.OK, Status.BAD_REQUEST, Status.FORBIDDEN, Status.NOT_FOUND,
    Status.CREATED, Status.TEMPORARY_REDIRECT, Status.INTERNAL_SERVER_ERROR, Status.CONFLICT,
    Status.UNAUTHORIZED, Status.NOT_MODIFIED)

  def statusCodes(implicit registry: MetricRegistry): Map[Int, Meter] = knownStatuses.map(s => s -> registry.meter(name(labelPrefix, s.toString))).toMap
  def requestsTimer(implicit registry: MetricRegistry): Timer = registry.timer(name(labelPrefix, "requestTimer"))
  def activeRequests(implicit registry: MetricRegistry): Counter = registry.counter(name(labelPrefix, "activeRequests"))
  def otherStatuses(implicit registry: MetricRegistry): Meter = registry.meter(name(labelPrefix, "other"))

  def apply(nextFilter: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    implicit val r = metricsModule.registry
    val context = requestsTimer.time()

      def logCompleted(result: Result): Unit = {
        activeRequests.dec()
        context.stop()
        statusCodes.getOrElse(result.header.status, otherStatuses).mark()
      }

    activeRequests.inc()
    nextFilter(rh).transform(
      result => {
        logCompleted(result)
        result
      },
      exception => {
        logCompleted(Results.InternalServerError)
        exception
      })
  }
}
