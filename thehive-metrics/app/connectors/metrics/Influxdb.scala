package connectors.metrics

import java.util
import java.util.SortedMap
import java.util.concurrent.TimeUnit
import javax.inject.{ Inject, Singleton }

import scala.collection.JavaConversions.{ iterableAsScalaIterable, mapAsScalaMap }
import scala.concurrent.ExecutionContext
import play.api.Logger
import play.api.libs.ws.WSClient
import com.codahale.metrics.{ Counter, Gauge, Histogram, Meter, MetricFilter, MetricRegistry, ScheduledReporter, Timer }

trait InfluxValue
case class InfluxLong(value: Long) extends InfluxValue {
  override def toString = s"${value}i"
}
case class InfluxFloat(value: Double) extends InfluxValue {
  override def toString: String = "%f".format(value)
}
case class InfluxString(value: String) extends InfluxValue {
  val escapedChars = ",=\""
  def escape(from: String): StringBuilder = {
    from.foldLeft(new StringBuilder) {
      case (sb, c) if escapedChars.contains(c) ⇒ sb.append(s"\\$c")
      case (sb, c)                             ⇒ sb.append(c)
    }
  }
  override def toString = s""""${escape(value)}""""
}

case class InfluxPoint(timestamp: Long, measurement: String, tags: Map[String, String], fields: (String, InfluxValue)*) {
  def lineProtocol: String = {
    val tagStr = if (tags.isEmpty)
      ""
    else
      tags
        .map { case (k, v) ⇒ s"$k=${InfluxString(v)}" }
        .mkString(",", ",", "")
    val fieldStr = fields
      .map { case (k, v) ⇒ s"$k=$v" }
      .mkString(",")
    s"$measurement$tagStr $fieldStr $timestamp"
  }
}

trait InfluxDBAPI {
  def send(points: InfluxPoint*): Unit
}

@Singleton
class InfluxDBFactory @Inject() (
    ws: WSClient,
    implicit val ec: ExecutionContext) {
  val log = Logger("InfluxDB")
  case class InfluxDB(url: String, user: String, password: String, database: String, retentionPolicy: String) extends InfluxDBAPI {
    def send(points: InfluxPoint*): Unit = {
      val x = ws
        .url(url.stripSuffix("/") + "/write")
        .withQueryString(
          "u" → user,
          "p" → password,
          "db" → database,
          "rp" → retentionPolicy)
        .withHeaders("Content-Type" → "text/plain")
        .post(points.map(_.lineProtocol).mkString("\n"))
        .map { response ⇒
          if ((response.status / 100) != 2)
            log.warn(s"Send metrics to InfluxDB error : ${response.body}")
        }
      ()
    }
  }
}

class InfluxDBReporter(
    influxdb: InfluxDBAPI,
    registry: MetricRegistry,
    filter: MetricFilter,
    rateUnit: TimeUnit,
    durationUnit: TimeUnit,
    tags: Map[String, String]) extends ScheduledReporter(registry, "influxdb-reporter", filter, rateUnit, durationUnit) {

  def report(
    gauges: util.SortedMap[String, Gauge[_]],
    counters: util.SortedMap[String, Counter],
    histograms: util.SortedMap[String, Histogram],
    meters: util.SortedMap[String, Meter],
    timers: util.SortedMap[String, Timer]): Unit = {

    val now = System.currentTimeMillis() * 1000000

    val points = gauges.map { case (name, gauge) ⇒ pointGauge(now, name, gauge) } ++
      counters.map { case (name, counter) ⇒ pointCounter(now, tags, name, counter) } ++
      histograms.map { case (name, histogram) ⇒ pointHistogram(now, tags, name, histogram) } ++
      meters.map { case (name, meter) ⇒ pointMeter(now, tags, name, meter) } ++
      timers.map { case (name, timer) ⇒ pointTimer(now, tags, name, timer) }
    influxdb.send(points.toSeq: _*)
  }

  def floatValue(value: Double): String = "%f" format value
  def stringValue(value: String): String = "\"" + value.replace("\"", "\\\"") + "\""

  def pointCounter(now: Long, tags: Map[String, String], name: String, counter: Counter): InfluxPoint = {
    InfluxPoint(now, name, tags, "value" → InfluxLong(counter.getCount))
  }

  def pointGauge(now: Long, name: String, gauge: Gauge[_]): InfluxPoint = {
    val value = gauge.getValue match {
      case s: String                ⇒ InfluxString(s)
      case i: java.lang.Iterable[_] ⇒ InfluxString(i.mkString(","))
      case d: Double                ⇒ InfluxFloat(d)
      case f: Float                 ⇒ InfluxFloat(f.toDouble)
      case l: Long                  ⇒ InfluxLong(l)
      case i: Int                   ⇒ InfluxLong(i.toLong)
      case o                        ⇒ sys.error(s"Can't convert object ${o.getClass} to influxDB value")
    }
    InfluxPoint(now, name, tags, "value" → value)
  }

  def pointHistogram(now: Long, tags: Map[String, String], name: String, histogram: Histogram): InfluxPoint = {
    val snapshot = histogram.getSnapshot
    InfluxPoint(now, name, tags,
      "value" → InfluxLong(histogram.getCount),
      "max" → InfluxLong(snapshot.getMax),
      "mean" → InfluxFloat(snapshot.getMean),
      "min" → InfluxLong(snapshot.getMin),
      "stddev" → InfluxFloat(snapshot.getStdDev),
      "p50" → InfluxFloat(snapshot.getMedian),
      "p75" → InfluxFloat(snapshot.get75thPercentile),
      "p95" → InfluxFloat(snapshot.get95thPercentile),
      "p98" → InfluxFloat(snapshot.get98thPercentile),
      "p99" → InfluxFloat(snapshot.get99thPercentile),
      "p999" → InfluxFloat(snapshot.get999thPercentile))
  }

  def pointMeter(now: Long, tags: Map[String, String], name: String, meter: Meter): InfluxPoint = {
    InfluxPoint(now, name, tags,
      "value" → InfluxLong(meter.getCount),
      "m1_rate" → InfluxFloat(meter.getOneMinuteRate),
      "m5_rate" → InfluxFloat(meter.getFiveMinuteRate),
      "m15_rate" → InfluxFloat(meter.getFifteenMinuteRate),
      "mean_rate" → InfluxFloat(meter.getMeanRate))
  }

  def pointTimer(now: Long, tags: Map[String, String], name: String, timer: Timer): InfluxPoint = {
    val snapshot = timer.getSnapshot
    InfluxPoint(now, name, tags,
      "max" → InfluxLong(snapshot.getMax),
      "mean" → InfluxFloat(snapshot.getMean),
      "min" → InfluxLong(snapshot.getMin),
      "stddev" → InfluxFloat(snapshot.getStdDev),
      "p50" → InfluxFloat(snapshot.getMedian),
      "p75" → InfluxFloat(snapshot.get75thPercentile),
      "p95" → InfluxFloat(snapshot.get95thPercentile),
      "p98" → InfluxFloat(snapshot.get98thPercentile),
      "p99" → InfluxFloat(snapshot.get99thPercentile),
      "p999" → InfluxFloat(snapshot.get999thPercentile),
      "value" → InfluxLong(timer.getCount),
      "m1_rate" → InfluxFloat(timer.getOneMinuteRate),
      "m5_rate" → InfluxFloat(timer.getFiveMinuteRate),
      "m15_rate" → InfluxFloat(timer.getFifteenMinuteRate),
      "mean_rate" → InfluxFloat(timer.getMeanRate))
  }
}