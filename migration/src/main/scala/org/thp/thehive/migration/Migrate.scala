package org.thp.thehive.migration

import java.io.File

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Configuration, Environment}
import scopt.OParser

import scala.collection.JavaConverters._
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext}

object Migrate extends App with MigrationOps {
  def getVersion: String = Option(getClass.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")
  def addConfig(config: Config, settings: (String, Any)*): Config =
    ConfigFactory.parseMap(Map(settings: _*).asJava).withFallback(config)

  val builder = OParser.builder[Config]
  val argParser = {
    import builder._
    OParser.sequence(
      programName("migrate"),
      version('v', "version"),
      help('h', "help"),
      head("TheHive migration tool", getVersion),
      opt[File]('c', "config")
        .valueName("<file>")
        .action((f, c) => ConfigFactory.parseFileAnySyntax(f).withFallback(c))
        .text("global configuration file"),
      opt[File]('i', "input")
        .valueName("<file>")
        .action((f, c) => addConfig(c, "input" -> ConfigFactory.parseFileAnySyntax(f).resolve().root()))
        .text("TheHive3 configuration file"),
      opt[File]('o', "output")
        .valueName("<file>")
        .action((f, c) => addConfig(c, "output" -> ConfigFactory.parseFileAnySyntax(f).resolve().root()))
        .text("TheHive4 configuration file"),
      opt[Unit]('d', "drop-database")
        .action((_, c) => addConfig(c, "output.dropDatabase" -> true))
        .text("Drop TheHive4 database before migration"),
      opt[String]('m', "main-organisation")
        .valueName("<organisation>")
        .action((o, c) => addConfig(c, "input.mainOrganisation" -> o)),
      opt[String]('u', "es-uri")
        .valueName("http://ip1:port,ip2:port")
        .text("TheHive3 ElasticSearch URI")
        .action((u, c) => addConfig(c, "input.search.uri" -> u)),
      opt[String]('i', "es-index")
        .valueName("<index>")
        .text("TheHive3 ElasticSearch index name")
        .action((i, c) => addConfig(c, "intput.search.index" -> i)),
      opt[Duration]('a', "es-keepalive")
        .valueName("<duration>")
        .text("TheHive3 ElasticSearch keepalive")
        .action((a, c) => addConfig(c, "input.search.keepalive" -> a.toString)),
      opt[Int]('p', "es-pagesize")
        .text("TheHive3 ElasticSearch page size")
        .action((p, c) => addConfig(c, "input.search.pagesize" -> p)),
      /* case age */
      opt[Duration]("max-case-age")
        .valueName("<duration>")
        .text("migrate only cases whose age is less than <duration>")
        .action((v, c) => addConfig(c, "input.filter.maxCaseAge" -> v.toString)),
      opt[Duration]("min-case-age")
        .valueName("<duration>")
        .text("migrate only cases whose age is greater than <duration>")
        .action((v, c) => addConfig(c, "input.filter.minCaseAge" -> v.toString)),
      opt[Duration]("case-from-date")
        .valueName("<date>")
        .text("migrate only cases created from <date>")
        .action((v, c) => addConfig(c, "input.filter.caseFromDate" -> v.toString)),
      opt[Duration]("case-until-date")
        .valueName("<date>")
        .text("migrate only cases created until <date>")
        .action((v, c) => addConfig(c, "input.filter.caseUntilDate" -> v.toString)),
      /* case number */
      opt[Duration]("case-from-number")
        .valueName("<number>")
        .text("migrate only cases from this case number")
        .action((v, c) => addConfig(c, "input.filter.caseFromNumber" -> v.toString)),
      opt[Duration]("case-until-number")
        .valueName("<number>")
        .text("migrate only cases until this case number")
        .action((v, c) => addConfig(c, "input.filter.caseUntilNumber" -> v.toString)),
      /* alert age */
      opt[Duration]("max-alert-age")
        .valueName("<duration>")
        .text("migrate only alerts whose age is less than <duration>")
        .action((v, c) => addConfig(c, "input.filter.maxAlertAge" -> v.toString)),
      opt[Duration]("min-alert-age")
        .valueName("<duration>")
        .text("migrate only alerts whose age is greater than <duration>")
        .action((v, c) => addConfig(c, "input.filter.minAlertAge" -> v.toString)),
      opt[Duration]("alert-from-date")
        .valueName("<date>")
        .text("migrate only alerts created from <date>")
        .action((v, c) => addConfig(c, "input.filter.alertFromDate" -> v.toString)),
      opt[Duration]("alert-until-date")
        .valueName("<date>")
        .text("migrate only alerts created until <date>")
        .action((v, c) => addConfig(c, "input.filter.alertUntilDate" -> v.toString)),
      /* audit age */
      opt[Duration]("max-audit-age")
        .valueName("<duration>")
        .text("migrate only audits whose age is less than <duration>")
        .action((v, c) => addConfig(c, "input.filter.minAuditAge" -> v.toString)),
      opt[Duration]("min-audit-age")
        .valueName("<duration>")
        .text("migrate only audits whose age is greater than <duration>")
        .action((v, c) => addConfig(c, "input.filter.maxAuditAge" -> v.toString)),
      opt[Duration]("audit-from-date")
        .valueName("<date>")
        .text("migrate only audits created from <date>")
        .action((v, c) => addConfig(c, "input.filter.auditFromDate" -> v.toString)),
      opt[Duration]("audit-until-date")
        .valueName("<date>")
        .text("migrate only audits created until <date>")
        .action((v, c) => addConfig(c, "input.filter.auditUntilDate" -> v.toString)),
      note("Accepted date formats are \"yyyyMMdd[HH[mm[ss]]]\" and \"MMdd\""),
      note(
        "The Format for duration is: <length> <unit>.\n" +
          "Accepted units are:\n" +
          "  DAY:         d, day\n" +
          "  HOUR:        h, hr, hour\n" +
          "  MINUTE:      m, min, minute\n" +
          "  SECOND:      s, sec, second\n" +
          "  MILLISECOND: ms, milli, millisecond"
      )
    )
  }
  val defaultConfig =
    ConfigFactory
      .parseResources("play/reference-overrides.conf")
      .withFallback(ConfigFactory.defaultReference())
      .resolve()
  OParser.parse(argParser, args, defaultConfig).foreach { config =>
    implicit val actorSystem: ActorSystem = ActorSystem("TheHiveMigration", config)
    implicit val ec: ExecutionContext     = actorSystem.dispatcher
    implicit val mat: Materializer        = Materializer(actorSystem)

    (new LogbackLoggerConfigurator).configure(Environment.simple(), Configuration.empty, Map.empty)

    val timer = actorSystem.scheduler.scheduleAtFixedRate(10.seconds, 10.seconds) { () =>
      logger.info(migrationStats.showStats())
      migrationStats.flush()
    }

    val returnStatus =
      try {
        val input  = th3.Input(Configuration(config.getConfig("input").withFallback(config)))
        val output = th4.Output(Configuration(config.getConfig("output").withFallback(config)))
        val filter = Filter.fromConfig(config.getConfig("input.filter"))

        val process = migrate(input, output, filter)

        Await.result(process, Duration.Inf)
        logger.info("Migration finished")
        0
      } catch {
        case e: Throwable =>
          logger.error(s"Migration failed", e)
          1
      } finally {
        timer.cancel()
        Await.ready(actorSystem.terminate(), 1.minute)
        ()
      }
    migrationStats.flush()
    logger.info(migrationStats.toString)
    System.exit(returnStatus)
  }
}
