package org.thp.thehive.migration

import java.io.File

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}

import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Configuration, Environment}

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.ConfigFactory

class Migration(input: Input, output: Output, implicit val ec: ExecutionContext, implicit val mat: Materializer) extends MigrationOps {}

object Start extends App with MigrationOps {

  val config =
    ConfigFactory
      .parseFileAnySyntax(new File("conf/migration.conf"))
      .withFallback(ConfigFactory.parseResources("play/reference-overrides.conf"))
      .withFallback(ConfigFactory.defaultReference())
      .resolve() // TODO read filename from argument
  implicit val actorSystem: ActorSystem     = ActorSystem("TheHiveMigration", config)
  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
  implicit val mat: Materializer            = Materializer(actorSystem)

  (new LogbackLoggerConfigurator).configure(Environment.simple(), Configuration.empty, Map.empty)

  val input = config.getString("input.name") match {
    case _ => th3.Input(Configuration(config.getConfig("input").withFallback(config)))
  }

  val output = config.getString("output.name") match {
    case _ => th4.Output(Configuration(config.getConfig("output").withFallback(config)))
  }

  val filter     = Filter.fromConfig(config.getConfig("input.filter"))
  val removeData = config.getBoolean("output.removeData")

  val process = migrate(input, output, filter, removeData)
  Await.ready(process, Duration.Inf)
  System.exit(0)
}
