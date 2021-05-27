package org.thp.thehive.cloner

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.tinkerpop.gremlin.structure.T
import org.thp.scalligraph.SingleInstance
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.connector.cortex.models.CortexSchemaDefinition
import org.thp.thehive.migration.Migrate.getVersion
import org.thp.thehive.models.TheHiveSchemaDefinition
import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Configuration, Environment}
import scopt.OParser

import java.io.File
import java.nio.file.{Files, Paths}
import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.util.Success

object Cloner extends App with IntegrityCheckApp {
  def getDatabase(configuration: Configuration)(implicit system: ActorSystem): Database = {
    val janusDatabase = JanusDatabase.openDatabase(configuration, system)
    new JanusDatabase(
      janusDatabase,
      configuration,
      system,
      new SingleInstance(true)
    )
  }

  val defaultLoggerConfigFile = "/etc/thehive/logback-cloner.xml"
  if (System.getProperty("logger.file") == null && Files.exists(Paths.get(defaultLoggerConfigFile)))
    System.setProperty("logger.file", defaultLoggerConfigFile)

  val builder = OParser.builder[Config]
  val argParser = {
    import builder._
    OParser.sequence(
      programName("clone"),
      version('v', "version"),
      help('h', "help"),
      head("TheHive cloner tool", getVersion),
      opt[File]('l', "logger-config")
        .valueName("<file>")
        .action { (f, c) =>
          System.setProperty("logger.file", f.getAbsolutePath)
          c
        }
        .text("logback configuration file"),
      opt[File]('c', "config")
        .valueName("<file>")
        .required()
        .action((f, c) => ConfigFactory.parseFileAnySyntax(f).withFallback(c))
        .text("configuration file")
    )
  }
  val defaultConfig =
    ConfigFactory
      .parseResources("play/reference-overrides.conf")
      .withFallback(ConfigFactory.defaultReference())
      .resolve()
  OParser.parse(argParser, args, defaultConfig).foreach { config =>
    implicit val actorSystem: ActorSystem = ActorSystem("TheHiveCloner", config)

    try {
      (new LogbackLoggerConfigurator).configure(Environment.simple(), Configuration.empty, Map.empty)

      val sourceDatabase: Database = getDatabase(
        Configuration(
          config
            .getConfig("from")
            .withoutPath("db.janusgraph.index.search")
        )
      )

      val thehiveSchema = new TheHiveSchemaDefinition
      val cortexSchema  = new CortexSchemaDefinition
      if (sourceDatabase.version(thehiveSchema.name) != thehiveSchema.operations.operations.length + 1) {
        println(
          "The schema of TheHive is not valid " +
            s"(found ${sourceDatabase.version(thehiveSchema.name)}, expected ${thehiveSchema.operations.operations.length + 1})"
        )
        sys.exit(1)
      }
      if (sourceDatabase.version(cortexSchema.name) != cortexSchema.operations.operations.length + 1) {
        println(
          "The schema of Cortex is not valid " +
            s"(found ${sourceDatabase.version(cortexSchema.name)}, expected ${cortexSchema.operations.operations.length + 1})"
        )
        sys.exit(1)
      }

      val destDatabase: Database = getDatabase(
        Configuration(
          config
            .getConfig("to")
        )
      )
      val nonEmpty: Boolean = destDatabase.roTransaction { implicit graph =>
        graph.VV().limit(1).exists
      }
      if (nonEmpty) {
        println("The destination database is not empty.")
        sys.exit(1)
      }

      println("Setting up the database schema ...")
      // don't create initial values
      val models = destDatabase.extraModels ++ thehiveSchema.modelList ++ cortexSchema.modelList
      destDatabase.createSchema(models)
      destDatabase.setVersion(thehiveSchema.name, thehiveSchema.operations.operations.length + 1)
      destDatabase.setVersion(cortexSchema.name, cortexSchema.operations.operations.length + 1)

      val batchSize: Int = config.getInt("batchSize")

      println("Copying vertices ...")
      sourceDatabase.roTransaction { from =>
        val vertexIdsBuilder = immutable.Map.newBuilder[AnyRef, AnyRef]

        val allVertices = from.VV().toIterator
        Iterator
          .continually(allVertices)
          .takeWhile(_.hasNext)
          .map(_.take(batchSize))
          .foreach { vv =>
            destDatabase.tryTransaction { to =>
              vv.foreach { vertex =>
                val properties    = T.label +: vertex.label +: vertex.properties[AnyRef]().asScala.flatMap(p => Seq(p.key(), p.value())).toSeq
                val createdVertex = to.underlying.addVertex(properties: _*)
                vertexIdsBuilder += vertex.id() -> createdVertex.id()
              }
              Success(())
            }
          }

        val vertexIds = vertexIdsBuilder.result()
        println("Copying edges ...")
        val allEdges = from.EE().toIterator
        Iterator
          .continually(allEdges)
          .takeWhile(_.hasNext)
          .map(_.take(batchSize))
          .foreach { ee =>
            destDatabase.tryTransaction { to =>
              ee.foreach { edge =>
                val createdEdge = for {
                  inVertex  <- vertexIds.get(edge.inVertex().id()).toIterable.flatMap(to.traversal().V(_).asScala)
                  outVertex <- vertexIds.get(edge.outVertex().id()).toIterable.flatMap(to.traversal().V(_).asScala)
                  properties = edge.properties[AnyRef]().asScala.flatMap(p => Seq(p.key(), p.value())).toSeq
                } yield outVertex.addEdge(edge.label, inVertex, properties: _*)
                if (createdEdge.isEmpty)
                  println(
                    s"Edge ${edge.label()}:${edge.id()} " +
                      s"from ${edge.outVertex().label()}:${edge.outVertex().id()} " +
                      s"to ${edge.inVertex().label()}:${edge.inVertex().id()} " +
                      s"cannot be created"
                  )
              }
              Success(())
            }
          }
      }
      sourceDatabase.close()

      println("Add indices ...")
      destDatabase.addSchemaIndexes(models)
      runChecks(destDatabase, Configuration(config))
      destDatabase.close()
    } finally {
      actorSystem.terminate()
      ()
    }
  }
}
