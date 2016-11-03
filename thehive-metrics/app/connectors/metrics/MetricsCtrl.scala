package connectors.metrics

import java.io.StringWriter

import javax.inject.{ Inject, Singleton }

import play.api.mvc.{ Action, Controller }
import play.api.routing.SimpleRouter
import play.api.routing.sird.{ GET, UrlContext }

import org.elastic4play.Timed

import connectors.Connector

@Singleton
class MetricsCtrl @Inject() (metricsModule: Metrics) extends Controller with Connector {

  val name = "metrics"

  val router = SimpleRouter {
    case GET(p"/stats") => stats
  }

  @Timed("controllers.MetricsCtrl.stats")
  def stats = Action {
    val writer = metricsModule.mapper.writerWithDefaultPrettyPrinter
    val stringWriter = new StringWriter()
    writer.writeValue(stringWriter, metricsModule.registry)
    Ok(stringWriter.toString).as("application/json").withHeaders("Cache-Control" -> "must-revalidate,no-cache,no-store")
  }
}