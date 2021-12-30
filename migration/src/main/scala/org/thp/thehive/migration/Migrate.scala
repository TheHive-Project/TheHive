package org.thp.thehive.migration

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Configuration, Environment}
import scopt.OParser

import java.io.File
import java.nio.file.{Files, Paths}
import scala.collection.JavaConverters._
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext}

object Migrate extends App with MigrationOps {
  val defaultLoggerConfigFile = "/etc/thehive/logback-migration.xml"
  if (System.getProperty("logger.file") == null && Files.exists(Paths.get(defaultLoggerConfigFile)))
    System.setProperty("logger.file", defaultLoggerConfigFile)

  def getVersion: String = Option(getClass.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def addConfig(config: Config, path: String, value: Any): Config =
    config.withValue(path, ConfigValueFactory.fromAnyRef(value))

  val builder = OParser.builder[Config]
  val argParser = {
    import builder._
    OParser.sequence(
      programName("migrate"),
      version('v', "version"),
      help('h', "help"),
      head("TheHive migration tool", getVersion),
      opt[File]('l', "logger-config")
        .valueName("<file>")
        .action { (f, c) =>
          System.setProperty("logger.file", f.getAbsolutePath)
          c
        }
        .text("logback configuration file"),
      opt[File]('c', "config")
        .valueName("<file>")
        .action((f, c) => ConfigFactory.parseFileAnySyntax(f).withFallback(c))
        .text("global configuration file"),
      opt[File]('i', "input")
        .valueName("<file>")
        .action((f, c) => addConfig(c, "input", ConfigFactory.parseFileAnySyntax(f).resolve().root().withFallback(c.getConfig("input"))))
        .text("TheHive3 configuration file"),
      opt[File]('o', "output")
        .valueName("<file>")
        .action((f, c) => addConfig(c, "output", ConfigFactory.parseFileAnySyntax(f).resolve().root().withFallback(c.getConfig("output"))))
        .text("TheHive4 configuration file"),
      opt[Unit]('d', "drop-database")
        .action((_, c) => addConfig(c, "output.dropDatabase", true))
        .text("Drop TheHive4 database before migration"),
      opt[String]('m', "main-organisation")
        .valueName("<organisation>")
        .action((o, c) => addConfig(c, "input.mainOrganisation", o)),
      opt[String]('u', "es-uri")
        .valueName("http://ip1:port,ip2:port")
        .text("TheHive3 ElasticSearch URI")
        .action((u, c) => addConfig(c, "input.search.uri", u)),
      opt[String]('e', "es-index")
        .valueName("<index>")
        .text("TheHive3 ElasticSearch index name")
        .action((i, c) => addConfig(c, "input.search.index", i)),
      opt[String]('x', "es-index-version")
        .valueName("<index>")
        .text("TheHive3 ElasticSearch index name version number (default: autodetect)")
        .action((i, c) => addConfig(c, "input.search.indexVersion", i)),
      opt[String]('a', "es-keepalive")
        .valueName("<duration>")
        .text("TheHive3 ElasticSearch keepalive")
        .action((a, c) => addConfig(c, "input.search.keepalive", a)),
      opt[Int]('p', "es-pagesize")
        .text("TheHive3 ElasticSearch page size")
        .action((p, c) => addConfig(c, "input.search.pagesize", p)),
      /* case age */
      opt[String]("max-case-age")
        .valueName("<duration>")
        .text("migrate only cases whose age is less than <duration>")
        .action((v, c) => addConfig(c, "input.filter.maxCaseAge", v)),
      opt[String]("min-case-age")
        .valueName("<duration>")
        .text("migrate only cases whose age is greater than <duration>")
        .action((v, c) => addConfig(c, "input.filter.minCaseAge", v)),
      opt[String]("case-from-date")
        .valueName("<date>")
        .text("migrate only cases created from <date>")
        .action((v, c) => addConfig(c, "input.filter.caseFromDate", v)),
      opt[String]("case-until-date")
        .valueName("<date>")
        .text("migrate only cases created until <date>")
        .action((v, c) => addConfig(c, "input.filter.caseUntilDate", v)),
      /* case number */
      opt[Int]("case-from-number")
        .valueName("<number>")
        .text("migrate only cases from this case number")
        .action((v, c) => addConfig(c, "input.filter.caseFromNumber", v)),
      opt[Int]("case-until-number")
        .valueName("<number>")
        .text("migrate only cases until this case number")
        .action((v, c) => addConfig(c, "input.filter.caseUntilNumber", v)),
      /* alert age */
      opt[String]("max-alert-age")
        .valueName("<duration>")
        .text("migrate only alerts whose age is less than <duration>")
        .action((v, c) => addConfig(c, "input.filter.maxAlertAge", v)),
      opt[String]("min-alert-age")
        .valueName("<duration>")
        .text("migrate only alerts whose age is greater than <duration>")
        .action((v, c) => addConfig(c, "input.filter.minAlertAge", v)),
      opt[String]("alert-from-date")
        .valueName("<date>")
        .text("migrate only alerts created from <date>")
        .action((v, c) => addConfig(c, "input.filter.alertFromDate", v)),
      opt[String]("alert-until-date")
        .valueName("<date>")
        .text("migrate only alerts created until <date>")
        .action((v, c) => addConfig(c, "input.filter.alertUntilDate", v)),
      opt[Seq[String]]("include-alert-types")
        .valueName("<type>,<type>...")
        .text("migrate only alerts with this types")
        .action((v, c) => addConfig(c, "input.filter.includeAlertTypes", v.asJava)),
      opt[Seq[String]]("exclude-alert-types")
        .valueName("<type>,<type>...")
        .text("don't migrate alerts with this types")
        .action((v, c) => addConfig(c, "input.filter.excludeAlertTypes", v.asJava)),
      opt[Seq[String]]("include-alert-sources")
        .valueName("<source>,<source>...")
        .text("migrate only alerts with this sources")
        .action((v, c) => addConfig(c, "input.filter.includeAlertSources", v.asJava)),
      opt[Seq[String]]("exclude-alert-sources")
        .valueName("<source>,<source>...")
        .text("don't migrate alerts with this sources")
        .action((v, c) => addConfig(c, "input.filter.excludeAlertSources", v.asJava)),
      /* audit age */
      opt[String]("max-audit-age")
        .valueName("<duration>")
        .text("migrate only audits whose age is less than <duration>")
        .action((v, c) => addConfig(c, "input.filter.minAuditAge", v)),
      opt[String]("min-audit-age")
        .valueName("<duration>")
        .text("migrate only audits whose age is greater than <duration>")
        .action((v, c) => addConfig(c, "input.filter.maxAuditAge", v)),
      opt[String]("audit-from-date")
        .valueName("<date>")
        .text("migrate only audits created from <date>")
        .action((v, c) => addConfig(c, "input.filter.auditFromDate", v)),
      opt[String]("audit-until-date")
        .valueName("<date>")
        .text("migrate only audits created until <date>")
        .action((v, c) => addConfig(c, "input.filter.auditUntilDate", v)),
      opt[Seq[String]]("include-audit-actions")
        .text("migration only audits with this action (Update, Creation, Delete)")
        .action((v, c) => addConfig(c, "input.filter.includeAuditActions", v.asJava)),
      opt[Seq[String]]("exclude-audit-actions")
        .text("don't migration audits with this action (Update, Creation, Delete)")
        .action((v, c) => addConfig(c, "input.filter.excludeAuditActions", v.asJava)),
      opt[Seq[String]]("include-audit-objectTypes")
        .text("migration only audits with this objectType (case, case_artifact, case_task, ...)")
        .action((v, c) => addConfig(c, "input.filter.includeAuditObjectTypes", v.asJava)),
      opt[Seq[String]]("exclude-audit-objectTypes")
        .text("don't migration audits with this objectType (case, case_artifact, case_task, ...)")
        .action((v, c) => addConfig(c, "input.filter.excludeAuditObjectTypes", v.asJava)),
      opt[Int]("case-number-shift")
        .text("transpose case number by adding this value")
        .action((v, c) => addConfig(c, "output.caseNumberShift", v)),
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

    try {
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
    } finally {
      actorSystem.terminate()
      ()
    }
  }
}
