package connectors.metrics

import java.util.concurrent.TimeUnit
import javax.inject.{ Inject, Provider, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ DurationLong, FiniteDuration }
import scala.language.implicitConversions

import play.api.{ Configuration, Environment, Logger }
import play.api.inject.ApplicationLifecycle
import play.api.mvc._

import org.aopalliance.intercept.{ MethodInterceptor, MethodInvocation }
import net.codingwell.scalaguice.ScalaMultibinder
import com.codahale.metrics._
import com.codahale.metrics.ganglia.GangliaReporter
import com.codahale.metrics.graphite.{ Graphite, GraphiteReporter }
import com.codahale.metrics.jvm.{ GarbageCollectorMetricSet, MemoryUsageGaugeSet, ThreadStatesGaugeSet }
import com.codahale.metrics.logback.InstrumentedAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.matcher.Matchers

import org.elastic4play.Timed
import ch.qos.logback.classic
import connectors.ConnectorModule
import info.ganglia.gmetric4j.gmetric.GMetric

trait UnitConverter {
  val validUnits: Set[Option[String]] = TimeUnit.values.map(v ⇒ Some(v.toString)).toSet + None
  implicit def stringToTimeUnit(s: String): TimeUnit = TimeUnit.valueOf(s)
}

case class GraphiteMetricConfig(host: String, port: Int, prefix: String, rateUnit: TimeUnit, durationUnit: TimeUnit, interval: FiniteDuration)
object GraphiteMetricConfig extends UnitConverter {
  def apply(configuration: Configuration): GraphiteMetricConfig = {
    val host = configuration.getOptional[String]("metrics.graphite.host").getOrElse("127.0.0.1")
    val port = configuration.getOptional[Int]("metrics.graphite.port").getOrElse(2003)
    val prefix = configuration.getOptional[String]("metrics.graphite.prefix").getOrElse("thehive")
    val rateUnit = configuration.getAndValidate[Option[String]]("metrics.graphite.rateUnit", validUnits).getOrElse("SECONDS")
    val durationUnit = configuration.getAndValidate[Option[String]]("metrics.graphite.durationUnit", validUnits).getOrElse("MILLISECONDS")
    val interval = configuration.getOptional[FiniteDuration]("metrics.graphite.period").getOrElse(10.seconds)
    GraphiteMetricConfig(host, port, prefix, rateUnit, durationUnit, interval)
  }
}

case class GangliaMetricConfig(host: String, port: Int, prefix: String, mode: String, ttl: Int, version: Boolean, rateUnit: TimeUnit, durationUnit: TimeUnit, tMax: Int, dMax: Int, interval: FiniteDuration)
object GangliaMetricConfig extends UnitConverter {
  def apply(configuration: Configuration): GangliaMetricConfig = {
    val host = configuration.getOptional[String]("metrics.ganglia.host").getOrElse("127.0.0.1")
    val port = configuration.getOptional[Int]("metrics.ganglia.port").getOrElse(8649)
    val prefix = configuration.getOptional[String]("metrics.ganglia.prefix").getOrElse("thehive")
    val validMode = GMetric.UDPAddressingMode.values().map(v ⇒ Some(v.toString)).toSet + None
    val mode = configuration.getAndValidate[Option[String]]("metrics.ganglia.mode", validMode).getOrElse("UNICAST")
    val ttl = configuration.getOptional[Int]("metrics.ganglia.ttl").getOrElse(1)
    val version = configuration.getAndValidate[Option[String]]("metrics.ganglia.version", Set(Some("3.0"), Some("3.1"), None)).forall(_ == 3.1)
    val rateUnit = configuration.getAndValidate[Option[String]]("metrics.ganglia.rateUnit", validUnits).getOrElse("SECONDS")
    val durationUnit = configuration.getAndValidate[Option[String]]("metrics.ganglia.durationUnit", validUnits).getOrElse("MILLISECONDS")
    val tMax = configuration.getOptional[Int]("metrics.ganglia.tmax").getOrElse(60)
    val dMax = configuration.getOptional[Int]("metrics.ganglia.dmax").getOrElse(0)
    val interval = configuration.getOptional[FiniteDuration]("metrics.ganglia.period").getOrElse(10.seconds)
    GangliaMetricConfig(host, port, prefix, mode, ttl, version, rateUnit, durationUnit, tMax, dMax, interval)
  }
}

case class InfluxMetricConfig(url: String, user: String, password: String, database: String, retention: String, tags: Map[String, String], interval: FiniteDuration)
object InfluxMetricConfig {
  def apply(configuration: Configuration): InfluxMetricConfig = {
    val url = configuration.getOptional[String]("metrics.influx.url").getOrElse("http://127.0.0.1:8086")
    val user = configuration.getOptional[String]("meorg.aopalliance.intercept.MethodInterceptortrics.influx.user").getOrElse("root")
    val password = configuration.getOptional[String]("metrics.influx.password").getOrElse("root")
    val database = configuration.getOptional[String]("metrics.influx.database").getOrElse("thehive")
    val retention = configuration.getOptional[String]("metrics.influx.retention").getOrElse("default")
    val tags = configuration.getOptional[Configuration]("metrics.influx.tags").fold(Map.empty[String, String]) { cfg ⇒
      cfg.entrySet.toMap.mapValues(_.render)
    }
    val interval = configuration.getOptional[FiniteDuration]("metrics.influx.period").getOrElse(10.seconds)
    InfluxMetricConfig(url, user, password, database, retention, tags, interval)
  }
}

case class MetricConfig(registryName: String, rateUnit: TimeUnit, durationUnit: TimeUnit, jvm: Boolean, logback: Boolean, graphiteMetricConfig: Option[GraphiteMetricConfig], gangliaMetricConfig: Option[GangliaMetricConfig], influxMetricConfig: Option[InfluxMetricConfig])
object MetricConfig extends UnitConverter {

  def apply(configuration: Configuration): MetricConfig = {
    val registryName = configuration.getOptional[String]("metrics.name").getOrElse("default")
    val rateUnit = configuration.getAndValidate[Option[String]]("metrics.rateUnit", validUnits).getOrElse("SECONDS")
    val durationUnit = configuration.getAndValidate[Option[String]]("metrics.durationUnit", validUnits).getOrElse("SECONDS")
    val jvm = configuration.getOptional[Boolean]("metrics.jvm").getOrElse(true)
    val logback = configuration.getOptional[Boolean]("metrics.logback").getOrElse(true)

    val graphiteMetricConfig = configuration.getOptional[Boolean]("metrics.graphite.enabled").filter(identity).map(_ ⇒ GraphiteMetricConfig(configuration))
    val gangliaMetricConfig = configuration.getOptional[Boolean]("metrics.ganglia.enabled").filter(identity).map(_ ⇒ GangliaMetricConfig(configuration))
    val influxMetricConfig = configuration.getOptional[Boolean]("metrics.influx.enabled").filter(identity).map(_ ⇒ InfluxMetricConfig(configuration))
    MetricConfig(registryName, rateUnit, durationUnit, jvm, logback, graphiteMetricConfig, gangliaMetricConfig, influxMetricConfig)
  }
}

@Singleton
class TimedInterceptor @Inject() (
    actionBuilderProvider: Provider[DefaultActionBuilder],
    metricsProvider: Provider[Metrics],
    ecProvider: Provider[ExecutionContext]) extends MethodInterceptor {
  implicit lazy val ec = ecProvider.get
  lazy val actionBuilder = actionBuilderProvider.get
  override def invoke(invocation: MethodInvocation): AnyRef = {
    val timerName = invocation.getStaticPart.getAnnotation(classOf[Timed]).value match {
      case "" ⇒
        val method = invocation.getMethod
        method.getClass.getName + "." + method.getName
      case _timerName ⇒ _timerName
    }
    val timer = metricsProvider.get.registry.timer(timerName).time()
    invocation.proceed() match {
      case f: Future[_] ⇒
        f.onComplete { _ ⇒ timer.stop() }
        f
      case action: Action[x] ⇒ actionBuilder.async(action.parser) { (request: Request[x]) ⇒
        val result: Future[Result] = action.apply(request)
        result.onComplete { _ ⇒ timer.stop() }
        result
      }

      case o ⇒
        timer.stop()
        o
    }
  }
}

class MetricsModule(
    environment: Environment,
    configuration: Configuration) extends ConnectorModule {
  def configure(): Unit = {
    if (configuration.getOptional[Boolean]("metrics.enabled").getOrElse(false)) {
      bind[MetricConfig].toInstance(MetricConfig(configuration))
      bind[Metrics].asEagerSingleton()
      val filterBindings = ScalaMultibinder.newSetBinder[Filter](binder)
      filterBindings.addBinding.to[MetricsFilterImpl]
      bindInterceptor(
        Matchers.any,
        Matchers.annotatedWith(classOf[org.elastic4play.Timed]),
        new TimedInterceptor(getProvider[DefaultActionBuilder], getProvider[Metrics], getProvider[ExecutionContext]))
      registerController[MetricsCtrl]
    }
    ()
  }
}

@Singleton
class Metrics @Inject() (configuration: Configuration, metricConfig: MetricConfig, influxDBFactory: InfluxDBFactory, lifecycle: ApplicationLifecycle) {
  val mapper = new ObjectMapper()

  val registry: MetricRegistry = SharedMetricRegistries.getOrCreate(metricConfig.registryName)

  if (metricConfig.jvm) {
    registry.register("jvm.attribute", new JvmAttributeGaugeSet())
    registry.register("jvm.gc", new GarbageCollectorMetricSet())
    registry.register("jvm.memory", new MemoryUsageGaugeSet())
    registry.register("jvm.threads", new ThreadStatesGaugeSet())
  }

  if (metricConfig.logback) {
    val appender: InstrumentedAppender = new InstrumentedAppender(registry)

    val logger = Logger(getClass)

    logger.underlyingLogger match {
      case cl: classic.Logger ⇒
        appender.setContext(cl.getLoggerContext)
        appender.start()
        cl.addAppender(appender)
      case l ⇒
        logger.error(s"Can't initialize logger metrics. Logger (${l.getClass} is not a logback classic logger).")
    }
  }

  mapper.registerModule(new json.MetricsModule(metricConfig.rateUnit, metricConfig.durationUnit, false))

  metricConfig.graphiteMetricConfig.foreach { graphiteMetricConfig ⇒
    val graphite = new Graphite(graphiteMetricConfig.host, graphiteMetricConfig.port)
    GraphiteReporter.forRegistry(registry)
      .prefixedWith(graphiteMetricConfig.prefix)
      .convertRatesTo(graphiteMetricConfig.rateUnit)
      .convertDurationsTo(graphiteMetricConfig.durationUnit)
      .filter(MetricFilter.ALL)
      .build(graphite)
      .start(graphiteMetricConfig.interval.toMillis, TimeUnit.MILLISECONDS)
  }

  metricConfig.gangliaMetricConfig.foreach { gangliaMetricConfig ⇒
    val ganglia = new GMetric(gangliaMetricConfig.host, gangliaMetricConfig.port, GMetric.UDPAddressingMode.valueOf(gangliaMetricConfig.mode), gangliaMetricConfig.ttl, gangliaMetricConfig.version)
    GangliaReporter.forRegistry(registry)
      .prefixedWith(gangliaMetricConfig.prefix)
      .convertRatesTo(gangliaMetricConfig.rateUnit)
      .convertDurationsTo(gangliaMetricConfig.durationUnit)
      .withTMax(gangliaMetricConfig.tMax)
      .withDMax(gangliaMetricConfig.dMax)
      .build(ganglia)
      .start(gangliaMetricConfig.interval.toMillis, TimeUnit.MILLISECONDS);
  }

  metricConfig.influxMetricConfig.foreach { influxMetricConfig ⇒
    val influxdb = influxDBFactory.InfluxDB(
      influxMetricConfig.url,
      influxMetricConfig.user,
      influxMetricConfig.password,
      influxMetricConfig.database,
      influxMetricConfig.retention)

    new InfluxDBReporter(
      influxdb,
      registry,
      MetricFilter.ALL,
      metricConfig.rateUnit,
      metricConfig.durationUnit,
      influxMetricConfig.tags)
      .start(influxMetricConfig.interval.toMillis, TimeUnit.MILLISECONDS)
  }

  lifecycle.addStopHook { () ⇒ Future.successful(SharedMetricRegistries.remove(metricConfig.registryName)) }
}
