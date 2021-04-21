package org.thp.thehive

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.Database
import play.api.{Configuration, Logger}

import java.io.File
import java.nio.file.{Files, Paths}
import scala.util.{Failure, Success, Try}

object TestAppBuilderLock

object JanusDatabaseProvider {
  lazy val logger: Logger = Logger(getClass)
  def withDatabase[A](databaseName: String, builder: Database => Try[Unit], actorSystem: ActorSystem)(body: Database => A): Try[A] = {
    val databaseDirectory: File = Files.createTempDirectory(Paths.get("target"), "janusgraph-test-database").toFile
    try TestAppBuilderLock
      .synchronized {
        if (!Files.exists(Paths.get(s"target/janusgraph-test-database-$databaseName"))) {
          logger.info("Test database doesn't exist, building it ...")
          val configuration = ConfigFactory
            .parseString(s"""
                            |db {
                            |  provider: janusgraph
                            |  janusgraph {
                            |    storage.backend: berkeleyje
                            |    storage.directory: "target/janusgraph-test-database-$databaseName/db"
                            |    berkeleyje.freeDisk: 2
                            |    index.search {
                            |      backend : lucene
                            |      directory: target/janusgraph-test-database-$databaseName/idx
                            |    }
                            |    connect {
                            |      maxAttempts = 10
                            |      minBackoff = 1 second
                            |      maxBackoff = 5 seconds
                            |      randomFactor = 0.2
                            |    }
                            |  }
                            |  onConflict {
                            |    maxAttempts = 6
                            |    minBackoff = 100 milliseconds
                            |    maxBackoff = 1 seconds
                            |    randomFactor = 0.2
                            |  }
                            |  chunkSize = 32k
                            |}
                            |
                            |akka.cluster.jmx.multi-mbeans-in-same-jvm: on
                            |""".stripMargin)
          Try(new JanusDatabase(Configuration(configuration), actorSystem, true))
            .flatMap { db =>
              builder(db).transform(
                { _ =>
                  logger.info("Test database build OK")
                  Success(())
                },
                { error =>
                  logger.error(s"Test database build fails", error)
                  db.close()
                  FileUtils.deleteDirectory(new File(s"target/janusgraph-test-database-$databaseName"))
                  Failure(error)
                }
              )
            }
        } else {
          logger.info("Test database already exists")
          Success(())
        }
      }
      .map { _ =>
        FileUtils.copyDirectory(new File(s"target/janusgraph-test-database-$databaseName"), databaseDirectory)
        val configuration = ConfigFactory
          .parseString(s"""
                          |db {
                          |  provider: janusgraph
                          |  janusgraph {
                          |    storage.backend: berkeleyje
                          |    storage.directory: "$databaseDirectory/db"
                          |    berkeleyje.freeDisk: 2
                          |    index.search {
                          |      backend : lucene
                          |      directory: $databaseDirectory/idx
                          |    }
                          |    connect {
                          |      maxAttempts = 10
                          |      minBackoff = 1 second
                          |      maxBackoff = 5 seconds
                          |      randomFactor = 0.2
                          |    }
                          |  }
                          |  onConflict {
                          |    maxAttempts = 6
                          |    minBackoff = 100 milliseconds
                          |    maxBackoff = 1 seconds
                          |    randomFactor = 0.2
                          |  }
                          |  chunkSize = 32k
                          |}
                          |
                          |akka.cluster.jmx.multi-mbeans-in-same-jvm: on
                          |""".stripMargin)
        val db = new JanusDatabase(Configuration(configuration), actorSystem, true)
        try body(db)
        finally db.close()
      } finally FileUtils.deleteDirectory(databaseDirectory)
  }
}
