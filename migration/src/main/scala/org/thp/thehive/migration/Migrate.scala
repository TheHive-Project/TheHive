package org.thp.thehive.migration

import java.io.File

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.collection.JavaConverters._

import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Configuration, Environment}

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import scopt.OParser
import scala.concurrent.duration.DurationInt

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
      opt[Duration]("max-case-age")
        .valueName("<duration>")
        .text("migrate only recent cases")
        .action((v, c) => addConfig(c, "input.filter.maxCaseAge" -> v.toString)),
      opt[Duration]("max-case-alert")
        .valueName("<duration>")
        .text("migrate only recent alerts")
        .action((v, c) => addConfig(c, "input.filter.maxAlertAge" -> v.toString)),
      opt[Duration]("max-audit-age")
        .valueName("<duration>")
        .text("migrate only recent audits")
        .action((v, c) => addConfig(c, "input.filter.maxAuditAge" -> v.toString))
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

    val input  = th3.Input(Configuration(config.getConfig("input").withFallback(config)))
    val output = th4.Output(Configuration(config.getConfig("output").withFallback(config)))
    val filter = Filter.fromConfig(config.getConfig("input.filter"))

    val process = migrate(input, output, filter)
    actorSystem.scheduler.scheduleAtFixedRate(10.seconds, 10.seconds) { () =>
      logger.info(migrationStats.showStats())
      migrationStats.flush()
    }
    Await.result(process, Duration.Inf)
    logger.info("Migration finished")
    migrationStats.flush()
    logger.info(migrationStats.toString)
    System.exit(0)
  }
}
