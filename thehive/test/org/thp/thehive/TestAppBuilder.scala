package org.thp.thehive

import akka.actor.ActorSystem
import akka.actor.typed.{ActorRef => TypedActorRef}
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import org.apache.commons.io.FileUtils
import org.thp.scalligraph.auth._
import org.thp.scalligraph.janus.JanusDatabaseProvider
import org.thp.scalligraph.models.{Database, Schema, UpdatableSchema}
import org.thp.scalligraph.query.QueryExecutor
import org.thp.scalligraph.services.{IntegrityCheck, LocalFileSystemStorageSrv, StorageSrv}
import org.thp.scalligraph.{AppBuilder, SingleInstance}
import org.thp.thehive.controllers.v0.TheHiveQueryExecutor
import org.thp.thehive.models.TheHiveSchemaDefinition
import org.thp.thehive.services.notification.notifiers.{AppendToFileProvider, EmailerProvider, NotifierProvider}
import org.thp.thehive.services.notification.triggers._
import org.thp.thehive.services.{UserSrv => _, _}

import java.io.File
import java.nio.file.{Files, Paths}
import javax.inject.{Inject, Provider, Singleton}
import scala.util.Try

object TestAppBuilderLock

trait TestAppBuilder {

  val databaseName: String = "default"

  def appConfigure: AppBuilder =
    (new AppBuilder)
      .bind[UserSrv, LocalUserSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchemaDefinition]
      .bindToProvider[TypedActorRef[CaseNumberActor.Request], TestNumberActorProvider]
      .multiBind[UpdatableSchema](classOf[TheHiveSchemaDefinition])
      .bindNamed[QueryExecutor, TheHiveQueryExecutor]("v0")
      .multiBind[AuthSrvProvider](classOf[LocalPasswordAuthProvider], classOf[LocalKeyAuthProvider], classOf[HeaderAuthProvider])
      .multiBind[NotifierProvider](classOf[AppendToFileProvider])
      .multiBind[NotifierProvider](classOf[EmailerProvider])
      .multiBind[TriggerProvider](classOf[LogInMyTaskProvider])
      .multiBind[TriggerProvider](classOf[CaseCreatedProvider])
      .multiBind[TriggerProvider](classOf[TaskAssignedProvider])
      .multiBind[TriggerProvider](classOf[AlertCreatedProvider])
      .bindToProvider[AuthSrv, MultiAuthSrvProvider]
      .bindInstance[SingleInstance](new SingleInstance(true))
      .multiBind[IntegrityCheck](
        classOf[ProfileIntegrityCheck],
        classOf[OrganisationIntegrityCheck],
        classOf[TagIntegrityCheck],
        classOf[UserIntegrityCheck],
        classOf[ImpactStatusIntegrityCheck],
        classOf[ResolutionStatusIntegrityCheck],
        classOf[ObservableTypeIntegrityCheck],
        classOf[CustomFieldIntegrityCheck],
        classOf[CaseTemplateIntegrityCheck],
        classOf[DataIntegrityCheck],
        classOf[CaseIntegrityCheck],
        classOf[AlertIntegrityCheck]
      )
      .bindActor[DummyActor]("config-actor")
      .bindActor[DummyActor]("notification-actor")
      .bindActor[DummyActor]("integrity-check-actor")
      .bindActor[DummyActor]("flow-actor")
      .addConfiguration("auth.providers = [{name:local},{name:key},{name:header, userHeader:user}]")
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
      .addConfiguration("play.mailer.mock = yes")
      .addConfiguration("play.mailer.debug = yes")
      .addConfiguration(s"storage.localfs.location = ${System.getProperty("user.dir")}/target/storage")
      .bindEagerly[ClusterSetup]

  def testApp[A](body: AppBuilder => A): A = {
    val storageDirectory = Files.createTempDirectory(Paths.get("target"), "janusgraph-test-database").toFile
    val indexDirectory   = Files.createTempDirectory(Paths.get("target"), storageDirectory.getName).toFile
    TestAppBuilderLock.synchronized {
      if (!Files.exists(Paths.get(s"target/janusgraph-test-database-$databaseName"))) {
        val app = appConfigure
          .addConfiguration(s"""
                               |db {
                               |  provider: janusgraph
                               |  janusgraph {
                               |    storage.backend: berkeleyje
                               |    storage.directory: "target/janusgraph-test-database-$databaseName"
                               |    berkeleyje.freeDisk: 2
                               |    index.search {
                               |      backend : lucene
                               |      directory: target/janusgraph-test-database-$databaseName-idx
                               |    }
                               |  }
                               |}
                               |akka.cluster.jmx.multi-mbeans-in-same-jvm: on
                               |""".stripMargin)
          .bindToProvider[Database, JanusDatabaseProvider]

        app[DatabaseBuilder].build()(app[Database])
        app[Database].close()
      }
      FileUtils.copyDirectory(new File(s"target/janusgraph-test-database-$databaseName"), storageDirectory)
      FileUtils.copyDirectory(new File(s"target/janusgraph-test-database-$databaseName-idx"), indexDirectory)
    }
    val app = appConfigure
      .bindToProvider[Database, JanusDatabaseProvider]
      .addConfiguration(s"""
                           |db {
                           |  provider: janusgraph
                           |  janusgraph {
                           |    storage.backend: berkeleyje
                           |    storage.directory: $storageDirectory
                           |    berkeleyje.freeDisk: 2
                           |    index.search {
                           |      backend : lucene
                           |      directory: $indexDirectory
                           |    }
                           |  }
                           |}
                           |""".stripMargin)

    try body(app)
    finally {
      Try(app[Database].close())
      FileUtils.deleteDirectory(storageDirectory)
      FileUtils.deleteDirectory(indexDirectory)
    }
  }
}

@Singleton
class BasicDatabaseProvider @Inject() (database: Database) extends Provider[Database] {
  override def get(): Database = database
}

class TestNumberActorProvider @Inject() (actorSystem: ActorSystem) extends Provider[TypedActorRef[CaseNumberActor.Request]] {
  override def get: TypedActorRef[CaseNumberActor.Request] =
    actorSystem
      .toTyped
      .systemActorOf(CaseNumberActor.caseNumberProvider(getNextNumber = () => 36, reloadTimer = () => (), nextNumber = 36), "case-number")
}
